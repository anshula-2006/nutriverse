package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.ChatHistoryItem;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.dto.RecommendationResponse;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.model.ChatMessage;
import com.nutriverse.backend.repository.NutritionProfileRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class RecommendationService {

    private static final int CANDIDATE_LIMIT = 12;

    // Product ranking criterion for per-100 g USDA records; not a regulatory label claim.
    private static final double MIN_PROTEIN_GRAMS_PER_100G = 8.0;

    private static final Set<String> GLUTEN_RISK_WORDS = Set.of(
            "wheat", "barley", "rye", "malt", "semolina",
            "bulgur", "couscous", "seitan", "spelt",
            "farro", "triticale", "oat", "oats", "oatmeal"
    );

    private static final Set<String> MEAT_WORDS = Set.of(
            "chicken", "turkey", "beef", "pork", "lamb", "mutton",
            "fish", "salmon", "tuna", "sardine", "shrimp", "prawn",
            "crab", "lobster", "meat", "ham", "bacon", "sausage",
            "poultry", "seafood", "duck", "goose", "venison", "goat",
            "cod", "haddock", "trout", "anchovy", "anchovies", "sardines",
            "prawns", "shellfish", "clam", "clams", "mussel",
            "mussels", "oyster", "oysters", "scallop", "scallops", "squid"
    );

    private static final Set<String> ANIMAL_PRODUCT_WORDS = Set.of(
            "egg", "eggs", "milk", "cheese", "yogurt", "curd",
            "paneer", "butter", "ghee", "cream", "whey", "yoghurt",
            "casein", "buttermilk", "kefir", "dairy"
    );

    private final NutritionProfileRepository profileRepository;
    private final NutritionLookupService nutritionLookupService;
    private final ProfileExtractionService profileExtractionService;
    private final GroqService groqService;
    private final ChatMemory chatMemory;

    public RecommendationService(
            NutritionProfileRepository profileRepository,
            NutritionLookupService nutritionLookupService,
            ProfileExtractionService profileExtractionService,
            GroqService groqService,
            ChatMemory chatMemory
    ) {
        this.profileRepository = profileRepository;
        this.nutritionLookupService = nutritionLookupService;
        this.profileExtractionService = profileExtractionService;
        this.groqService = groqService;
        this.chatMemory = chatMemory;
    }

    public boolean isStructuredFollowup(String userId, String request) {
        if (!GroqService.isAlternativeFollowup(request)
                || GroqService.isRecipeRequest(request)) {
            return false;
        }

        List<ChatMessage> history = chatMemory.getRecentMessages(userId);

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);

            if ("assistant".equals(message.getRole())) {
                return message.getRecommendation() != null;
            }

            if ("user".equals(message.getRole())) {
                return false;
            }
        }

        return false;
    }

    public List<ChatHistoryItem> getChatHistory(String userId) {
        return chatMemory.getRecentMessages(userId).stream()
                .map(message -> new ChatHistoryItem(
                        message.getRole(),
                        message.getContent(),
                        message.getRecommendation()
                ))
                .toList();
    }

    public RecommendationResponse recommend(
            String userId,
            String request
    ) {
        if (request == null || request.isBlank()) {
            return new RecommendationResponse(
                    "EVIDENCE_STRUCTURED",
                    "NOT_SPECIFIED",
                    "NOT_SPECIFIED",
                    List.of()
            );
        }

        // Important: recommendation requests bypass /api/chat, so profile extraction
        // must also happen here.
        profileExtractionService.processUserMessage(userId, request);

        NutritionProfile profile =
                profileRepository.findByUserId(userId).orElse(null);

        List<ChatMessage> previous = chatMemory.getRecentRecommendations(userId);
        boolean followup = GroqService.isAlternativeFollowup(request);
        String resolvedRequest = followup && !previous.isEmpty()
                ? previous.getLast().getRecommendationRequest() : request;
        if (!resolvedRequest.equals(request) && !currentExclusions(request).isEmpty()) {
            resolvedRequest += "\n" + request;
        }
        // Keep new exclusions/constraints supplied along with an alternative request.
        String text = normalize(resolvedRequest + " " + request);
        String diet = getDiet(profile, text);
        String meal = getMeal(text);

        boolean wantsProtein =
                hasAny(text, "protein", "high protein",
                        "post workout", "post-workout");

        boolean wantsFiber =
                hasAny(text, "fiber", "fibre",
                        "high fiber", "high fibre");

        boolean glutenFree =
                "GLUTEN_FREE".equals(
                        profile == null
                                ? null
                                : profile.getDietaryRestriction()
                )
                        || hasAny(
                        text,
                        "celiac", "coeliac",
                        "gluten free", "gluten-free",
                        "can't eat gluten",
                        "cannot eat gluten",
                        "avoid gluten"
                );

        Set<String> excluded =
                new LinkedHashSet<>();

        excluded.addAll(savedDislikes(profile));
        excluded.addAll(currentExclusions(resolvedRequest));
        excluded.addAll(currentExclusions(request));

        Set<String> recent = new LinkedHashSet<>();
        if (followup) {
            for (ChatMessage message : previous) {
                for (var item : message.getRecommendation().getRecommendations()) {
                    excluded.add(item.getWhat());
                    if (item.getEvidence() != null) recent.add(item.getEvidence().getSourceId());
                }
            }
        }

        List<String> candidateNames =
                groqService.generateFoodCandidates(
                        userId,
                        resolvedRequest.equals(request) ? request : resolvedRequest + "\nFollow-up: " + request,
                        excluded,
                        CANDIDATE_LIMIT
                );

        List<ScoredFood> foods = new ArrayList<>();
        Set<String> seenSourceIds = new HashSet<>();
        int failures = 0;

        for (String candidate : candidateNames) {

            String cleanCandidate =
                    cleanCandidate(candidate);

            if (cleanCandidate == null) continue;

            if (matchesAny(
                    normalize(cleanCandidate),
                    excluded)) {
                continue;
            }

            if (!dietAllows(cleanCandidate, diet))
                continue;

            if (glutenFree
                    && containsAnyWord(
                    cleanCandidate,
                    GLUTEN_RISK_WORDS)) {
                continue;
            }

            try {
                NutritionResult food =
                        findBestVerified(cleanCandidate, diet, glutenFree, excluded, recent);

                if (food == null
                        || !seenSourceIds.add(
                        food.getSourceId())) {
                    continue;
                }

                if (wantsProtein && !meetsProteinFocus(food)) {
                    continue;
                }

                double score =
                        score(
                                food,
                                profile,
                                wantsProtein,
                                wantsFiber
                        );

                foods.add(
                        new ScoredFood(
                                cleanCandidate,
                                food,
                                score
                        )
                );

            } catch (RuntimeException e) {
                failures++;
            }
        }

        foods.sort(
                Comparator
                        .comparingDouble(
                                ScoredFood::score
                        )
                        .reversed()
                        .thenComparing(
                                item ->
                                        item.food()
                                                .getFoodName() == null
                                                ? item.candidate()
                                                : item.food()
                                                .getFoodName()
                        )
        );

        List<RecommendationResponse.RecommendationItem> result =
                new ArrayList<>();

        for (ScoredFood ranked : foods) {

            if (result.size() == 3)
                break;

            try {
                NutritionResult exact =
                        nutritionLookupService.findBySourceId(
                                ranked.food().getSource(),
                                ranked.food().getSourceId()
                        );

                if (!isVerified(exact)
                        || !allowedFood(exact, diet, glutenFree, excluded, recent)
                        || wantsProtein && !meetsProteinFocus(exact))
                    continue;

                result.add(
                        buildItem(
                                ranked.candidate(),
                                exact,
                                profile,
                                diet,
                                meal,
                                wantsProtein,
                                wantsFiber,
                                glutenFree
                        )
                );

                // Exclude duplicate foods even when candidates resolve to different records.
                excluded.add(exact.getFoodName());
                recent.add(exact.getSourceId());

            } catch (RuntimeException e) {
                failures++;
            }
        }

        if (result.isEmpty()
                && failures > 0) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Verified nutrition evidence is temporarily unavailable."
            );
        }

        RecommendationResponse response = new RecommendationResponse(
                "EVIDENCE_STRUCTURED",
                diet == null
                        ? "NOT_SPECIFIED"
                        : diet,
                profile == null
                        || profile.getGoal() == null
                        ? "NOT_SPECIFIED"
                        : profile.getGoal(),
                result
        );
        chatMemory.addRecommendation(userId, request, resolvedRequest, response);
        return response;
    }

    private NutritionResult findBestVerified(
            String candidate, String diet, boolean glutenFree,
            Set<String> excluded, Set<String> recent
    ) {
        List<NutritionResult> results =
                nutritionLookupService.search(
                        candidate
                );

        if (results == null
                || results.isEmpty()) {
            return null;
        }

        NutritionResult best = null;
        int bestScore = -1;

        for (NutritionResult food : results) {

            if (!isVerified(food) || !allowedFood(food, diet, glutenFree, excluded, recent))
                continue;

            int relevance =
                    relevance(
                            candidate,
                            food.getFoodName()
                    );

            if (relevance > bestScore) {
                best = food;
                bestScore = relevance;
            }
        }

        return bestScore <= 0
                ? null
                : best;
    }

    private int relevance(
            String candidate,
            String foodName
    ) {
        if (candidate == null
                || foodName == null) {
            return 0;
        }

        Set<String> candidateTokens =
                meaningfulTokens(candidate);

        Set<String> foodTokens =
                meaningfulTokens(foodName);

        int score = 0;

        for (String token : candidateTokens) {
            if (foodTokens.contains(token))
                score += 3;
        }

        String a = normalize(candidate);
        String b = normalize(foodName);

        if (b.contains(a))
            score += 8;

        if (a.contains(b))
            score += 4;

        return score;
    }

    private Set<String> meaningfulTokens(
            String value
    ) {
        Set<String> tokens =
                new LinkedHashSet<>();

        for (String token :
                normalize(value).split(" ")) {

            if (token.length() < 3)
                continue;

            if (Set.of(
                    "cooked", "raw", "boiled",
                    "with", "without", "and",
                    "the", "prepared"
            ).contains(token)) {
                continue;
            }

            tokens.add(token);
        }

        return tokens;
    }

    private boolean isVerified(
            NutritionResult food
    ) {
        return food != null
                && "USDA FoodData Central".equals(
                food.getSource())
                && "AUTHORITATIVE_DATABASE".equals(
                food.getSourceType())
                && food.isVerified()
                && !food.isEstimated()
                && food.getSourceId() != null
                && food.getSourceId()
                .matches("[1-9][0-9]*")
                && food.getServingSize() != null
                && Double.isFinite(food.getServingSize())
                && food.getServingSize() > 0
                && "g".equals(
                food.getServingUnit());
    }

    private String getDiet(
            NutritionProfile profile,
            String text
    ) {
        String saved = profile == null ? null : profile.getDietType();
        if ("VEGAN".equals(saved) || text.contains("vegan")) return "VEGAN";
        if ("VEGETARIAN".equals(saved)) return "VEGETARIAN";
        if (hasAny(
                text,
                "non vegetarian",
                "nonveg",
                "non veg"
        )) {
            return "NON_VEGETARIAN";
        }

        if (text.contains("vegan"))
            return "VEGAN";

        if (text.contains("vegetarian"))
            return "VEGETARIAN";

        return profile == null
                ? null
                : profile.getDietType();
    }

    private String getMeal(String text) {

        if (text.contains("breakfast"))
            return "BREAKFAST";

        if (text.contains("lunch"))
            return "LUNCH";

        if (text.contains("dinner"))
            return "DINNER";

        if (hasAny(
                text,
                "snack",
                "snacks",
                "evening snack"
        )) {
            return "SNACK";
        }

        return null;
    }

    private boolean dietAllows(
            String candidate,
            String diet
    ) {
        if (diet == null
                || "NON_VEGETARIAN".equals(diet)) {
            return true;
        }

        if (containsAnyWord(
                candidate,
                MEAT_WORDS)) {
            return false;
        }

        if ("VEGAN".equals(diet)
                && containsAnyWord(
                candidate,
                ANIMAL_PRODUCT_WORDS)) {
            return false;
        }

        return true;
    }

    private boolean allowedFood(NutritionResult food, String diet, boolean glutenFree,
                                Set<String> excluded, Set<String> recent) {
        String name = food.getFoodName();
        return name != null && !name.isBlank()
                && dietAllows(name, diet)
                && (!glutenFree || !containsAnyWord(name, GLUTEN_RISK_WORDS))
                && !matchesAny(normalize(name), excluded)
                && !recent.contains(food.getSourceId());
    }

    private double score(
            NutritionResult food,
            NutritionProfile profile,
            boolean wantsProtein,
            boolean wantsFiber
    ) {
        double score = 0;

        Double protein =
                food.getProtein();

        Double fiber =
                food.getFiber();

        Double calories =
                food.getCalories();

        if (wantsProtein
                && valid(protein)) {
            score += protein * 2;
        }

        if (wantsFiber
                && valid(fiber)) {
            score += fiber * 2;
        }

        if (profile != null
                && profile.getGoal() != null) {

            switch (profile.getGoal()) {

                case "WEIGHT_LOSS" -> {
                    if (valid(protein)
                            && valid(calories)
                            && calories > 0) {
                        score +=
                                (protein / calories)
                                        * 300;
                    }

                    if (valid(fiber))
                        score += fiber;
                }

                case "FITNESS",
                     "WEIGHT_GAIN" -> {
                    if (valid(protein))
                        score += protein * 1.5;
                }

                case "HEALTHY_EATING",
                     "MAINTENANCE" -> {
                    if (valid(protein))
                        score += protein;

                    if (valid(fiber))
                        score += fiber;
                }

                default -> {
                }
            }
        }

        return score;
    }

    private RecommendationResponse.RecommendationItem buildItem(
            String candidate,
            NutritionResult food,
            NutritionProfile profile,
            String diet,
            String meal,
            boolean protein,
            boolean fiber,
            boolean glutenFree
    ) {
        List<String> why =
                new ArrayList<>();

        if (diet != null) {
            why.add(
                    "Filtered for your "
                            + label(diet)
                            + " dietary preference."
            );
        }

        if (meal != null) {
            why.add(
                    "Generated for the requested "
                            + meal.toLowerCase(
                            Locale.ROOT)
                            + "."
            );
        }

        if (profile != null
                && profile.getGoal() != null) {
            why.add(
                    "Your "
                            + label(
                            profile.getGoal())
                            + " goal was considered."
            );
        }

        if (glutenFree) {
            why.add(
                    "Celiac-focused filtering removed obvious gluten-grain "
                            + "options. Check packaged-food labels and avoid cross-contact."
            );
        }

        if (matchesPreference(
                profile,
                candidate)) {
            why.add(
                    "Matches a food preference saved in your profile."
            );
        }

        String reason =
                protein
                        && valid(
                        food.getProtein())
                        ? "This option passed NutriVerse's protein-focused filter "
                        + "(at least 8 g protein per 100 g)."
                        : fiber
                        && valid(
                        food.getFiber())
                        ? "Verified fiber evidence supports your request."
                        : "This option matched your personalization filters and has "
                        + "a verified nutrition record.";

        RecommendationResponse.Evidence evidence =
                new RecommendationResponse.Evidence(
                        food.getServingSize(),
                        food.getServingUnit(),
                        food.getCalories(),
                        food.getProtein(),
                        food.getFiber(),
                        food.getSource(),
                        food.getSourceId(),
                        food.getDataType(),
                        food.isVerified()
                );

        return new RecommendationResponse.RecommendationItem(
                food.getFoodName() == null
                        ? candidate
                        : food.getFoodName(),
                why,
                evidence,
                reason
        );
    }

    private boolean matchesPreference(
            NutritionProfile profile,
            String candidate
    ) {
        if (profile == null
                || profile.getFoodPreferences() == null) {
            return false;
        }

        String preferences =
                normalize(
                        profile.getFoodPreferences());

        for (String token :
                meaningfulTokens(candidate)) {

            if (preferences.contains(token))
                return true;
        }

        return false;
    }

    private Set<String> savedDislikes(
            NutritionProfile profile
    ) {
        if (profile == null
                || profile.getFoodDislikes() == null
                || profile.getFoodDislikes().isBlank()) {
            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        for (String item :
                profile.getFoodDislikes()
                        .split(",")) {

            String value =
                    normalize(item);

            if (!value.isBlank())
                result.add(value);
        }

        return result;
    }

    private Set<String> currentExclusions(
            String text
    ) {
        if (text.contains("\n")) {
            Set<String> combined = new LinkedHashSet<>();
            for (String line : text.split("\\R")) combined.addAll(currentExclusions(line));
            return combined;
        }
        text = normalize(text);
        boolean exclusionRequest =
                hasAny(
                        text,
                        "don't have",
                        "dont have",
                        "do not have",
                        "not available",
                        "without ",
                        "exclude ",
                        "avoid ",
                        "instead of ",
                        "don't like",
                        "do not like",
                        "dislike",
                        "hate "
                );

        if (!exclusionRequest)
            return Set.of();

        String remainder =
                text.replaceFirst(
                        ".*?(?:don't have|dont have|do not have|not available|without|"
                                + "exclude|avoid|instead of|don't like|do not like|dislike|hate)\\s+",
                        ""
                );

        Set<String> result =
                new LinkedHashSet<>();

        for (String part :
                remainder.split(
                        "\\s*(?:,|\\bor\\b|\\band\\b)\\s*"
                )) {

            String value =
                    normalize(part);

            if (!value.isBlank()
                    && value.length() <= 50) {
                result.add(value);
            }
        }

        return result;
    }

    private boolean matchesAny(
            String candidate,
            Collection<String> excluded
    ) {
        for (String value : excluded) {

            String blocked =
                    normalize(value);

            if (blocked.isBlank())
                continue;

            if (candidate.contains(blocked)
                    || blocked.contains(candidate)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsAnyWord(
            String value,
            Collection<String> words
    ) {
        String normalized =
                " " + normalize(value) + " ";

        for (String word : words) {

            String target =
                    " " + normalize(word) + " ";

            if (normalized.contains(target))
                return true;
        }

        return false;
    }

    private String cleanCandidate(
            String value
    ) {
        if (value == null)
            return null;

        String cleaned =
                value.replace("*", "")
                        .trim();

        if (cleaned.isBlank()
                || cleaned.length() > 70
                || cleaned.contains(":")) {
            return null;
        }

        return cleaned;
    }

    private String normalize(String value) {

        return value == null
                ? ""
                : value.toLowerCase(
                        Locale.ROOT)
                .replace("-", " ")
                .replace("’", "'")
                .replaceAll(
                        "[^a-z0-9' ]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private boolean hasAny(
            String text,
            String... words
    ) {
        return Arrays.stream(words)
                .anyMatch(text::contains);
    }

    private boolean meetsProteinFocus(NutritionResult food) {
        return food != null
                && valid(food.getProtein())
                && food.getProtein() >= MIN_PROTEIN_GRAMS_PER_100G
                && Double.valueOf(100.0).equals(food.getServingSize())
                && "g".equals(food.getServingUnit());
    }

    private boolean valid(
            Double value
    ) {
        return value != null
                && Double.isFinite(value)
                && value >= 0;
    }

    private String label(String value) {

        return value.replace(
                        "_",
                        " "
                )
                .toLowerCase(
                        Locale.ROOT);
    }

    private record ScoredFood(
            String candidate,
            NutritionResult food,
            double score
    ) {
    }
}
