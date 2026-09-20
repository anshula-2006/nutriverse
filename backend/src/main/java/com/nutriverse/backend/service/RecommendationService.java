package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.dto.RecommendationResponse;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class RecommendationService {

    private final NutritionProfileRepository profileRepository;
    private final NutritionLookupService nutritionLookupService;

    public RecommendationService(
            NutritionProfileRepository profileRepository,
            NutritionLookupService nutritionLookupService
    ) {
        this.profileRepository = profileRepository;
        this.nutritionLookupService = nutritionLookupService;
    }

    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate("Oats", "oats cooked", true, true, true, "BREAKFAST,SNACK"),
            new Candidate("Tofu", "tofu raw", true, true, true, "BREAKFAST,LUNCH,DINNER"),
            new Candidate("Peanut Butter", "peanut butter smooth", true, true, true, "BREAKFAST,SNACK"),
            new Candidate("Plain Yogurt", "yogurt plain", false, true, true, "BREAKFAST,SNACK"),
            new Candidate("Cottage Cheese", "cottage cheese", false, true, true, "BREAKFAST,SNACK"),
            new Candidate("Lentils", "lentils cooked", true, true, true, "LUNCH,DINNER"),
            new Candidate("Chickpeas", "chickpeas cooked", true, true, true, "LUNCH,DINNER,SNACK"),
            new Candidate("Chicken Breast", "chicken breast cooked", false, false, true, "LUNCH,DINNER")
    );

    public RecommendationResponse recommend(String userId, String request) {

        NutritionProfile profile =
                profileRepository.findByUserId(userId).orElse(null);

        String text = request.toLowerCase(Locale.ROOT);
        String diet = getDiet(profile, text);
        String meal = getMeal(text);

        boolean protein = hasAny(text, "protein", "high protein", "post workout", "post-workout");
        boolean fiber = hasAny(text, "fiber", "fibre", "high fiber", "high fibre");

        Set<String> excludedFoods = getExcludedFoods(text);

        List<ScoredFood> foods = new ArrayList<>();
        int failures = 0;

        for (Candidate candidate : CANDIDATES) {

            if (excludedFoods.contains(normalize(candidate.name()))) continue;
            if (!matchesDiet(candidate, diet)) continue;
            if (meal != null && !candidate.meals().contains(meal)) continue;

            try {
                NutritionResult food = findVerified(candidate.query());

                if (food != null) {
                    foods.add(new ScoredFood(
                            candidate,
                            food,
                            score(candidate, food, profile, protein, fiber)
                    ));
                }
            } catch (RuntimeException e) {
                failures++;
            }
        }

        foods.sort(Comparator.comparingDouble(ScoredFood::score).reversed());

        List<RecommendationResponse.RecommendationItem> result =
                new ArrayList<>();

        for (ScoredFood ranked : foods) {

            if (result.size() == 3) break;

            try {
                NutritionResult exact =
                        nutritionLookupService.findBySourceId(
                                ranked.food().getSource(),
                                ranked.food().getSourceId()
                        );

                if (isVerified(exact)) {
                    result.add(buildItem(
                            ranked.candidate(),
                            exact,
                            profile,
                            diet,
                            meal,
                            protein,
                            fiber
                    ));
                }
            } catch (RuntimeException e) {
                failures++;
            }
        }

        if (result.isEmpty() && failures > 0) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Verified nutrition evidence is temporarily unavailable."
            );
        }

        return new RecommendationResponse(
                "EVIDENCE_STRUCTURED",
                diet == null ? "NOT_SPECIFIED" : diet,
                profile == null || profile.getGoal() == null
                        ? "NOT_SPECIFIED"
                        : profile.getGoal(),
                result
        );
    }

    private NutritionResult findVerified(String query) {

        for (NutritionResult food : nutritionLookupService.search(query)) {
            if (isVerified(food)) return food;
        }

        return null;
    }

    private boolean isVerified(NutritionResult food) {

        return food != null
                && "USDA FoodData Central".equals(food.getSource())
                && "AUTHORITATIVE_DATABASE".equals(food.getSourceType())
                && food.isVerified()
                && !food.isEstimated()
                && food.getSourceId() != null
                && food.getSourceId().matches("[1-9][0-9]*")
                && food.getServingSize() != null
                && food.getServingSize() > 0
                && "g".equals(food.getServingUnit());
    }

    private String getDiet(NutritionProfile profile, String text) {

        if (hasAny(text, "non-vegetarian", "non vegetarian", "nonveg", "non veg"))
            return "NON_VEGETARIAN";

        if (text.contains("vegan"))
            return "VEGAN";

        if (text.contains("vegetarian"))
            return "VEGETARIAN";

        return profile == null ? null : profile.getDietType();
    }

    private String getMeal(String text) {

        if (text.contains("breakfast")) return "BREAKFAST";
        if (text.contains("lunch")) return "LUNCH";
        if (text.contains("dinner")) return "DINNER";
        if (hasAny(text, "snack", "snacks", "evening snack")) return "SNACK";

        return null;
    }

    private boolean matchesDiet(Candidate c, String diet) {

        if (diet == null) return true;

        return switch (diet) {
            case "VEGAN" -> c.vegan();
            case "VEGETARIAN" -> c.vegetarian();
            case "NON_VEGETARIAN" -> c.nonVegetarian();
            default -> false;
        };
    }

    private double score(
            Candidate candidate,
            NutritionResult food,
            NutritionProfile profile,
            boolean wantsProtein,
            boolean wantsFiber
    ) {

        double score = 0;

        Double protein = food.getProtein();
        Double fiber = food.getFiber();
        Double calories = food.getCalories();

        if (wantsProtein && valid(protein))
            score += protein * 2;

        if (wantsFiber && valid(fiber))
            score += fiber * 2;

        if (profile != null && profile.getGoal() != null) {

            switch (profile.getGoal()) {

                case "WEIGHT_LOSS" -> {
                    if (valid(protein) && valid(calories) && calories > 0)
                        score += (protein / calories) * 300;

                    if (valid(fiber))
                        score += fiber;
                }

                case "FITNESS", "WEIGHT_GAIN" -> {
                    if (valid(protein))
                        score += protein * 1.5;
                }

                case "HEALTHY_EATING", "MAINTENANCE" -> {
                    if (valid(protein)) score += protein;
                    if (valid(fiber)) score += fiber;
                }

                default -> {
                }
            }
        }

        if (likes(profile, candidate.name()))
            score += 5;

        return score;
    }

    private RecommendationResponse.RecommendationItem buildItem(
            Candidate candidate,
            NutritionResult food,
            NutritionProfile profile,
            String diet,
            String meal,
            boolean protein,
            boolean fiber
    ) {

        List<String> why = new ArrayList<>();

        if (diet != null)
            why.add("Matches your " + label(diet) + " dietary preference.");

        if (meal != null)
            why.add("Suitable for the requested " + meal.toLowerCase(Locale.ROOT) + ".");

        if (profile != null && profile.getGoal() != null)
            why.add("Your " + label(profile.getGoal()) + " goal was considered.");

        if (likes(profile, candidate.name()))
            why.add("Matches a food preference saved in your profile.");

        String reason =
                protein && valid(food.getProtein())
                        ? "Verified protein evidence supports your high-protein request."
                        : fiber && valid(food.getFiber())
                          ? "Verified fiber evidence supports your request."
                          : "This food matched your profile rules and has a verified nutrition record.";

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
                food.getFoodName() == null ? candidate.name() : food.getFoodName(),
                why,
                evidence,
                reason
        );
    }

    private boolean likes(NutritionProfile profile, String food) {

        return profile != null
                && profile.getFoodPreferences() != null
                && profile.getFoodPreferences()
                .toLowerCase(Locale.ROOT)
                .contains(food.toLowerCase(Locale.ROOT));
    }

    private Set<String> getExcludedFoods(String text) {

        String normalized = normalize(text);

        boolean exclusionRequest = hasAny(
                normalized,
                "don't have",
                "dont have",
                "do not have",
                "not available",
                "without ",
                "exclude ",
                "avoid ",
                "instead of "
        );

        if (!exclusionRequest) {
            return Collections.emptySet();
        }

        Set<String> excluded = new HashSet<>();

        for (Candidate candidate : CANDIDATES) {

            String name = normalize(candidate.name());

            if (normalized.contains(name)
                    || ("plain yogurt".equals(name) && normalized.contains("yogurt"))
                    || ("cottage cheese".equals(name) && normalized.contains("paneer"))
                    || ("chicken breast".equals(name) && normalized.contains("chicken"))) {

                excluded.add(name);
            }
        }

        return excluded;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                .replace("-", " ")
                .trim();
    }

    private boolean hasAny(String text, String... words) {
        return Arrays.stream(words).anyMatch(text::contains);
    }

    private boolean valid(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private String label(String value) {
        return value.replace("_", " ").toLowerCase(Locale.ROOT);
    }

    private record Candidate(
            String name,
            String query,
            boolean vegan,
            boolean vegetarian,
            boolean nonVegetarian,
            String meals
    ) {}

    private record ScoredFood(
            Candidate candidate,
            NutritionResult food,
            double score
    ) {}
}