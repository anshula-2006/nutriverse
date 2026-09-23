package com.nutriverse.backend.service;
import com.nutriverse.backend.dto.ChatHistoryItem;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.dto.RecommendationResponse;
import com.nutriverse.backend.model.ChatMessage;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class RecommendationService {
    private static final int CANDIDATE_LIMIT = 12;
    private static final int RESULT_LIMIT = 3;
    private static final double MIN_PROTEIN = 8.0;
    private static final Set<String> GLUTEN = Set.of( "wheat", "barley", "rye", "malt", "semolina", "bulgur", "couscous", "seitan", "spelt", "farro", "triticale", "oats", "maida", "bread", "breadcrumbs" );
    private static final Set<String> MEAT = Set.of( "chicken", "turkey", "beef", "pork", "lamb", "mutton", "fish", "salmon", "tuna", "shrimp", "prawn", "crab", "lobster", "meat", "ham", "bacon", "sausage", "seafood", "duck", "goat", "shellfish" );
    private static final Set<String> ANIMAL = Set.of( "egg", "eggs", "milk", "cheese", "yogurt", "curd", "paneer", "butter", "ghee", "cream", "whey", "dairy" );
    private final NutritionProfileRepository profiles;
    private final NutritionLookupService lookup;
    private final ProfileExtractionService extraction;
    private final GroqService groq;
    private final ChatMemory memory;
    public RecommendationService( NutritionProfileRepository profiles, NutritionLookupService lookup, ProfileExtractionService extraction, GroqService groq, ChatMemory memory ) {
        this.profiles = profiles;
        this.lookup = lookup;
        this.extraction = extraction;
        this.groq = groq;
        this.memory = memory;
    }
    public List<ChatHistoryItem> getChatHistory(String userId) {
        return memory.getRecentMessages(userId).stream() .map(m -> new ChatHistoryItem( m.getRole(), m.getContent(), m.getRecommendation())) .toList();
    }
    public long clearChatHistory(String userId) {
        extraction.clearPendingState(userId);
        return memory.clearHistory(userId);
    }
    public boolean isStructuredFollowup(String userId, String request) {
        if (!GroqService.isAlternativeFollowup(request) || GroqService.isRecipeRequest(request)) return false;
        List<ChatMessage> history = memory.getRecentMessages(userId);
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if ("assistant".equals(m.getRole())) return m.getRecommendation() != null;
            if ("user".equals(m.getRole())) return false;
        }
        return false;
    }
    public RecommendationResponse recommend(String userId, String request) {
        if (request == null || request.isBlank()) return empty();
        extraction.processUserMessage(userId, request);
        NutritionProfile profile = profiles.findByUserId(userId).orElse(null);
        List<ChatMessage> previous = memory.getRecentRecommendations(userId);
        boolean followup = GroqService.isAlternativeFollowup(request);
        String resolved = resolveRequest(request, previous, followup);
        String text = normalize(resolved + " " + request);
        String diet = diet(profile, text);
        String meal = meal(text);
        boolean glutenFree = glutenFree(profile, text);
        String religiousDiet = profile == null ? null : profile.getReligiousDiet();
        boolean proteinFocus = has(text, "protein", "high protein", "post workout");
        boolean fiberFocus = has(text, "fiber", "fibre", "high fiber", "high fibre");
        Set<String> excluded = exclusions(profile, resolved, request);
        Set<String> recentIds = new LinkedHashSet<>();
        if (followup) addPrevious(previous, excluded, recentIds);
        String prompt = resolved.equals(request) ? request : resolved + "\nFollow-up: " + request;
        List<String> candidates = groq.generateFoodCandidates( userId, prompt, excluded, CANDIDATE_LIMIT);
        List<ScoredFood> ranked = rank( candidates, profile, diet, glutenFree, religiousDiet, proteinFocus, fiberFocus, excluded, recentIds);
        List<RecommendationResponse.RecommendationItem> items = buildItems( ranked, profile, diet, meal, glutenFree, religiousDiet, proteinFocus, fiberFocus, excluded, recentIds);
        RecommendationResponse response = new RecommendationResponse( "EVIDENCE_STRUCTURED", diet == null ? "NOT_SPECIFIED" : diet, profile == null || profile.getGoal() == null ? "NOT_SPECIFIED" : profile.getGoal(), items );
        memory.addRecommendation(userId, request, resolved, response);
        return response;
    }
    private List<ScoredFood> rank( List<String> candidates, NutritionProfile profile, String diet, boolean glutenFree, String religiousDiet, boolean proteinFocus, boolean fiberFocus, Set<String> excluded, Set<String> recentIds ) {
        List<ScoredFood> ranked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int failures = 0;
        for (String raw : candidates) {
            String candidate = clean(raw);
            if (candidate == null || matches(candidate, excluded) || !dietAllows(candidate, diet) || (glutenFree && contains(candidate, GLUTEN))) {
                continue;
            }
            try {
                NutritionResult food = findBest( candidate, diet, glutenFree, religiousDiet, excluded, recentIds);
                if (food == null || !seen.add(food.getSourceId())) continue;
                if (proteinFocus && !proteinEnough(food)) continue;
                ranked.add(new ScoredFood( candidate, food, score(food, profile, proteinFocus, fiberFocus) ));
            } catch (RuntimeException e) {
                failures++;
            }
        }
        ranked.sort(Comparator.comparingDouble(ScoredFood::score).reversed());
        if (ranked.isEmpty() && failures > 0) throw new ResponseStatusException( HttpStatus.SERVICE_UNAVAILABLE, "Verified nutrition evidence is temporarily unavailable.");
        return ranked;
    }
    private NutritionResult findBest( String candidate, String diet, boolean glutenFree, String religiousDiet, Set<String> excluded, Set<String> recentIds ) {
        NutritionResult best = null;
        int bestScore = -1;
        for (NutritionResult food : lookup.search(candidate)) {
            if (!verified(food) || !allowed(food, diet, glutenFree, religiousDiet, excluded, recentIds)) {
                continue;
            }
            int score = relevance(candidate, food.getFoodName());
            if (score > bestScore) {
                best = food;
                bestScore = score;
            }
        }
        return best;
    }
    private List<RecommendationResponse.RecommendationItem> buildItems( List<ScoredFood> ranked, NutritionProfile profile, String diet, String meal, boolean glutenFree, String religiousDiet, boolean proteinFocus, boolean fiberFocus, Set<String> excluded, Set<String> recentIds ) {
        List<RecommendationResponse.RecommendationItem> items = new ArrayList<>();
        for (ScoredFood scored : ranked) {
            if (items.size() >= RESULT_LIMIT) break;
            try {
                NutritionResult food = lookup.findBySourceId( scored.food().getSource(), scored.food().getSourceId());
                if (!verified(food) || !allowed(food, diet, glutenFree, religiousDiet, excluded, recentIds) || (proteinFocus && !proteinEnough(food))) {
                    continue;
                }
                items.add(item( scored.candidate(), food, profile, diet, meal, glutenFree, religiousDiet, proteinFocus, fiberFocus));
                excluded.add(food.getFoodName());
                recentIds.add(food.getSourceId());
            } catch (RuntimeException ignored) {
            }
        }
        return items;
    }
    private RecommendationResponse.RecommendationItem item( String candidate, NutritionResult food, NutritionProfile profile, String diet, String meal, boolean glutenFree, String religiousDiet, boolean proteinFocus, boolean fiberFocus ) {
        List<String> why = new ArrayList<>();
        if (diet != null) why.add("Filtered for your " + label(diet) + " dietary preference.");
        if (meal != null) why.add("Generated for the requested " + meal.toLowerCase(Locale.ROOT) + ".");
        if (profile != null && profile.getGoal() != null) why.add("Your " + label(profile.getGoal()) + " goal was considered.");
        if (glutenFree) why.add("Gluten-related conflicts were screened for your gluten-free requirement.");
        if (religiousDiet != null) why.add(label(religiousDiet) + " dietary conflict screening was applied. " + "UNKNOWN does not mean certified compliant.");
        if (preference(profile, candidate)) why.add("Matches a saved food preference.");
        String reason;
        if (proteinFocus) {
            reason = "This food passed the protein-focused filter with " + "at least 8 g protein per 100 g and has verified nutrition evidence.";
        } else if (fiberFocus && valid(food.getFiber())) {
            reason = "Verified fiber evidence supports this recommendation.";
        } else {
            reason = "Verified nutrition evidence supports this recommendation.";
        }
        RecommendationResponse.Evidence evidence = new RecommendationResponse.Evidence( food.getServingSize(), food.getServingUnit(), food.getCalories(), food.getProtein(), food.getFiber(), food.getSource(), food.getSourceId(), food.getDataType(), food.isVerified() );
        return new RecommendationResponse.RecommendationItem( food.getFoodName() == null ? candidate : food.getFoodName(), why, evidence, reason);
    }
    private boolean allowed( NutritionResult food, String diet, boolean glutenFree, String religiousDiet, Set<String> excluded, Set<String> recentIds ) {
        if (food == null || food.getFoodName() == null) return false;
        String name = food.getFoodName();
        if (!dietAllows(name, diet)) return false;
        if (glutenFree && ("CONFLICT".equals(food.getGlutenStatus()) || contains(name, GLUTEN))) return false;
        if (!religiousAllows(food, religiousDiet)) return false;
        if (matches(name, excluded)) return false;
        return !recentIds.contains(food.getSourceId());
    }
    private boolean religiousAllows(NutritionResult food, String diet) {
        if (diet == null) return true;
        String status = switch (diet) {
            case "JAIN" -> food.getJainStatus();
            case "HALAL" -> food.getHalalStatus();
            case "KOSHER" -> food.getKosherStatus();
            default -> null;
        };
        return !"CONFLICT".equals(status);
    }
    private boolean dietAllows(String food, String diet) {
        if (diet == null || "NON_VEGETARIAN".equals(diet)) return true;
        if (contains(food, MEAT)) return false;
        return !"VEGAN".equals(diet) || !contains(food, ANIMAL);
    }
    private boolean verified(NutritionResult food) {
        if (food == null || food.getSource() == null) return false;
        boolean authoritative = ("USDA FoodData Central".equals(food.getSource()) || "ICMR-NIN IFCT 2017".equals(food.getSource())) && "AUTHORITATIVE_DATABASE".equals(food.getSourceType());
        boolean product = "Open Food Facts".equals(food.getSource()) && "PRODUCT_DATABASE".equals(food.getSourceType());
        return (authoritative || product) && food.isVerified() && !food.isEstimated() && food.getSourceId() != null && food.getSourceId().matches("[A-Za-z0-9._-]{1,32}") && food.getServingSize() != null && food.getServingSize() > 0 && "g".equals(food.getServingUnit());
    }
    private boolean proteinEnough(NutritionResult food) {
        return food != null && valid(food.getProtein()) && food.getProtein() >= MIN_PROTEIN && Double.valueOf(100.0).equals(food.getServingSize()) && "g".equals(food.getServingUnit());
    }
    private double score( NutritionResult food, NutritionProfile profile, boolean proteinFocus, boolean fiberFocus ) {
        double score = 0;
        if (proteinFocus && valid(food.getProtein())) score += food.getProtein() * 2;
        if (fiberFocus && valid(food.getFiber())) score += food.getFiber() * 2;
        if (profile == null || profile.getGoal() == null) return score;
        Double p = food.getProtein();
        Double f = food.getFiber();
        Double c = food.getCalories();
        switch (profile.getGoal()) {
            case "WEIGHT_LOSS" -> {
                if (valid(p) && valid(c) && c > 0) score += (p / c) * 300;
                if (valid(f)) score += f;
            }
            case "FITNESS", "WEIGHT_GAIN" -> {
                if (valid(p)) score += p * 1.5;
            }
            case "HEALTHY_EATING", "MAINTENANCE" -> {
                if (valid(p)) score += p;
                if (valid(f)) score += f;
            }
            default -> { }
        }
        return score;
    }
    private int relevance(String candidate, String name) {
        if (name == null) return 0;
        String a = normalize(candidate);
        String b = normalize(name);
        int score = b.contains(a) ? 8 : 0;
        for (String word : a.split(" ")) if (word.length() >= 3 && b.contains(word)) score += 3;
        return score;
    }
    private String diet(NutritionProfile profile, String text) {
        String saved = profile == null ? null : profile.getDietType();
        if ("VEGAN".equals(saved) || text.contains("vegan")) return "VEGAN";
        if ("VEGETARIAN".equals(saved)) return "VEGETARIAN";
        if (has(text, "non vegetarian", "non veg", "nonveg")) return "NON_VEGETARIAN";
        if (text.contains("vegetarian")) return "VEGETARIAN";
        return saved;
    }
    private boolean glutenFree(NutritionProfile profile, String text) {
        return "GLUTEN_FREE".equals( profile == null ? null : profile.getDietaryRestriction()) || has(text, "celiac", "coeliac", "gluten free", "gluten-free", "can't eat gluten", "cannot eat gluten", "avoid gluten");
    }
    private String meal(String text) {
        if (text.contains("breakfast")) return "BREAKFAST";
        if (text.contains("lunch")) return "LUNCH";
        if (text.contains("dinner")) return "DINNER";
        if (text.contains("snack")) return "SNACK";
        return null;
    }
    private String resolveRequest( String request, List<ChatMessage> previous, boolean followup ) {
        if (!followup || previous.isEmpty()) return request;
        String old = previous.getLast().getRecommendationRequest();
        if (old == null || old.isBlank()) return request;
        return currentExclusions(request).isEmpty() ? old : old + "\n" + request;
    }
    private void addPrevious( List<ChatMessage> previous, Set<String> excluded, Set<String> recentIds ) {
        for (ChatMessage message : previous) {
            if (message.getRecommendation() == null) continue;
            for (var item : message.getRecommendation().getRecommendations()) {
                if (item.getWhat() != null) excluded.add(item.getWhat());
                if (item.getEvidence() != null && item.getEvidence().getSourceId() != null) recentIds.add(item.getEvidence().getSourceId());
            }
            excluded.addAll( currentExclusions(message.getRecommendationRequest()));
        }
    }
    private Set<String> exclusions( NutritionProfile profile, String resolved, String request ) {
        Set<String> result = new LinkedHashSet<>();
        if (profile != null && profile.getFoodDislikes() != null) for (String food : profile.getFoodDislikes().split(",")) {
            String clean = normalize(food);
            if (!clean.isBlank()) result.add(clean);
        }
        result.addAll(currentExclusions(resolved));
        result.addAll(currentExclusions(request));
        return result;
    }
    private Set<String> currentExclusions(String value) {
        if (value == null || value.isBlank()) return Set.of();
        if (value.contains("\n")) {
            Set<String> all = new LinkedHashSet<>();
            for (String line : value.split("\\R")) all.addAll(currentExclusions(line));
            return all;
        }
        String text = normalize(value);
        if (!has(text, "don't have", "dont have", "do not have", "not available", "without ", "exclude ", "avoid ", "instead of ", "don't like", "dislike", "hate ")) return Set.of();
        String rest = text.replaceFirst( ".*?(?:don't have|dont have|do not have|not available|without|" + "exclude|avoid|instead of|don't like|dislike|hate)\\s+", "");
        Set<String> result = new LinkedHashSet<>();
        for (String part : rest.split("\\s*(?:,|\\bor\\b|\\band\\b)\\s*")) {
            String food = normalize(part);
            if (!food.isBlank() && food.length() <= 50) result.add(food);
        }
        return result;
    }
    private boolean preference(NutritionProfile profile, String candidate) {
        if (profile == null || profile.getFoodPreferences() == null) return false;
        String preferences = normalize(profile.getFoodPreferences());
        for (String word : normalize(candidate).split(" ")) if (word.length() >= 3 && preferences.contains(word)) return true;
        return false;
    }
    private boolean matches(String food, Collection<String> blocked) {
        String text = " " + normalize(food) + " ";
        for (String value : blocked) {
            String word = normalize(value);
            if (!word.isBlank() && text.contains(" " + word + " ")) return true;
        }
        return false;
    }
    private boolean contains(String food, Collection<String> words) {
        String text = " " + normalize(food) + " ";
        for (String word : words) if (text.contains(" " + normalize(word) + " ")) return true;
        return false;
    }
    private String clean(String value) {
        if (value == null) return null;
        String result = value.replace("*", "").trim();
        return result.isBlank() || result.length() > 70 || result.contains(":") ? null : result;
    }
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT) .replace("-", " ") .replace("’", "'") .replaceAll("[^a-z0-9' ]", " ") .replaceAll("\\s+", " ") .trim();
    }
    private boolean has(String text, String... values) {
        return Arrays.stream(values).anyMatch(text::contains);
    }
    private boolean valid(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }
    private String label(String value) {
        return value.replace("_", " ").toLowerCase(Locale.ROOT);
    }
    private RecommendationResponse empty() {
        return new RecommendationResponse( "EVIDENCE_STRUCTURED", "NOT_SPECIFIED", "NOT_SPECIFIED", List.of());
    }
    private record ScoredFood( String candidate, NutritionResult food, double score ) {}
}
