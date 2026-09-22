package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.ChatResponse;
import com.nutriverse.backend.dto.NutritionResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompositeMealService {

    private final NutritionLookupService lookup;
    private final ChatMemory memory;

    private static final Pattern INGREDIENT = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(kg|g|mg)\\s+([^,;\\n]+)"
    );

    private static final Pattern SERVINGS = Pattern.compile(
            "(?i)(?:makes?|serves?)\\s*(\\d+)|(\\d+)\\s*servings?"
    );

    private static final Pattern NUTRIENT = Pattern.compile(
            "(?i)\\b(calories?|kcal|protein|carbs?|carbohydrates?|fat|"
                    + "fiber|fibre|sodium|potassium|calcium|iron|nutrition|macros?)\\b"
    );

    private static final Pattern FOOD_QUERY = Pattern.compile(
            "(?i)\\b(?:calories?|kcal|protein|carbs?|carbohydrates?|fat|"
                    + "fiber|fibre|sodium|potassium|calcium|iron|nutrition|macros?)\\b"
                    + ".*?\\b(?:in|of|for)\\s+(.+)$"
    );

    private static final Pattern FOOD_HAVE = Pattern.compile(
            "(?i)\\b(?:does|do)\\s+(.+?)\\s+(?:have|contain|contains)\\b"
    );

    public CompositeMealService(
            NutritionLookupService lookup,
            ChatMemory memory
    ) {
        this.lookup = lookup;
        this.memory = memory;
    }

    // =====================================================
    // MAIN FLOW
    // =====================================================

    public ChatResponse handle(String userId, String message) {

        if (message == null || message.isBlank())
            return null;

        Context pending = findPending(userId);

        // Continue ingredient-based estimation
        if (pending != null && hasDetails(message)) {

            String details = pending.details().isBlank()
                    ? message
                    : pending.details() + ", " + message;

            ChatResponse response =
                    calculate(pending.dish(), details);

            save(userId, message, response);
            return response;
        }

        // Only handle nutrition questions
        if (!NUTRIENT.matcher(message).find())
            return null;

        String dish = dishName(message);

        if (dish == null)
            return null;

        ChatResponse response;

        // Explicit homemade dish → directly ask ingredients
        if (isHomemade(message)) {
            response = prompt(dish, false);

        } else {

            // First try verified USDA
            NutritionResult food = findGoodUsdaMatch(dish);

            if (food != null) {
                response = new ChatResponse(
                        formatNutrition(
                                food,
                                requestedNutrient(message)
                        )
                );

            } else {
                // USDA does not have a reliable match
                response = prompt(dish, true);
            }
        }

        save(userId, message, response);
        return response;
    }

    // =====================================================
    // USDA LOOKUP
    // =====================================================

    private NutritionResult findGoodUsdaMatch(String query) {

        try {
            List<NutritionResult> results =
                    lookup.search(query);

            if (results == null)
                return null;

            NutritionResult best = null;
            int bestScore = 0;

            for (NutritionResult food : results) {

                if (!validUsda(food))
                    continue;

                int score =
                        matchScore(
                                query,
                                food.getFoodName()
                        );

                if (score > bestScore) {
                    best = food;
                    bestScore = score;
                }
            }

            // Avoid loosely related USDA records
            if (best == null || bestScore < 70)
                return null;

            NutritionResult exact =
                    lookup.findBySourceId(
                            best.getSource(),
                            best.getSourceId()
                    );

            return validUsda(exact)
                    ? exact
                    : null;

        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean validUsda(NutritionResult food) {

        return food != null
                && food.isVerified()
                && !food.isEstimated()
                && "USDA FoodData Central".equals(food.getSource())
                && food.getSourceId() != null
                && food.getServingSize() != null
                && food.getServingSize() > 0
                && "g".equalsIgnoreCase(food.getServingUnit());
    }

    private int matchScore(String query, String foodName) {

        Set<String> queryWords = words(query);
        Set<String> foodWords = words(foodName);

        if (queryWords.isEmpty())
            return 0;

        int matches = 0;

        for (String word : queryWords) {
            if (foodWords.contains(word))
                matches++;
        }

        return (int) Math.round(
                100.0 * matches / queryWords.size()
        );
    }

    private Set<String> words(String text) {

        Set<String> words = new HashSet<>();

        for (String word : normalize(text).split(" ")) {

            if (word.length() < 2)
                continue;

            // Basic plural normalization
            if (word.length() > 3 && word.endsWith("s"))
                word = word.substring(0, word.length() - 1);

            words.add(word);
        }

        return words;
    }

    // =====================================================
    // COMPOSITE CALCULATION
    // =====================================================

    private ChatResponse calculate(
            String dish,
            String text
    ) {

        List<Ingredient> ingredients =
                parseIngredients(text);

        Integer servings =
                parseServings(text);

        if (ingredients.isEmpty())
            return prompt(dish, false);

        if (servings == null) {
            return new ChatResponse(
                    "I have the ingredients. Tell me how many servings "
                            + "the recipe made. Example: makes 4 servings."
            );
        }

        double[] total = new double[9];
        boolean[] available = new boolean[9];

        Arrays.fill(available, true);

        List<ChatResponse.IngredientEvidence> evidence =
                new ArrayList<>();

        for (Ingredient ingredient : ingredients) {

            NutritionResult food =
                    findGoodUsdaMatch(ingredient.name());

            if (food == null) {
                return new ChatResponse(
                        "I couldn't find a verified USDA ingredient match for \""
                                + ingredient.name()
                                + "\". Try a simpler ingredient name."
                );
            }

            double factor =
                    ingredient.grams()
                            / food.getServingSize();

            addNutrition(
                    total,
                    available,
                    food,
                    factor
            );

            evidence.add(
                    new ChatResponse.IngredientEvidence(
                            ingredient.name(),
                            round(ingredient.grams()),
                            food.getFoodName(),
                            food.getSource(),
                            food.getSourceId()
                    )
            );
        }

        NutritionResult nutrition =
                buildResult(
                        dish,
                        total,
                        available,
                        servings
                );

        ChatResponse.CompositeMeal meal =
                new ChatResponse.CompositeMeal(
                        dish,
                        servings,
                        nutrition,
                        evidence,
                        List.of(
                                "Estimated from verified USDA ingredient records.",
                                "Cooking and preparation may change final nutrition values."
                        )
                );

        return new ChatResponse(
                "Estimated nutrition per serving using verified USDA ingredients.",
                meal
        );
    }

    private List<Ingredient> parseIngredients(String text) {

        List<Ingredient> result =
                new ArrayList<>();

        Matcher matcher =
                INGREDIENT.matcher(text);

        while (matcher.find()) {

            double amount =
                    Double.parseDouble(matcher.group(1));

            String unit =
                    matcher.group(2).toLowerCase();

            double grams = switch (unit) {
                case "kg" -> amount * 1000;
                case "mg" -> amount / 1000;
                default -> amount;
            };

            String name = matcher.group(3)
                    .replaceAll(
                            "(?i)\\s*(?:makes?|serves?).*$",
                            ""
                    )
                    .trim();

            if (grams > 0 && !name.isBlank())
                result.add(
                        new Ingredient(name, grams)
                );
        }

        return result;
    }

    private Integer parseServings(String text) {

        Matcher matcher =
                SERVINGS.matcher(text);

        if (!matcher.find())
            return null;

        String value =
                matcher.group(1) != null
                        ? matcher.group(1)
                        : matcher.group(2);

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =====================================================
    // NUTRIENT MATH
    // =====================================================

    private void addNutrition(
            double[] total,
            boolean[] available,
            NutritionResult food,
            double factor
    ) {

        Double[] values = {
                food.getCalories(),
                food.getProtein(),
                food.getCarbs(),
                food.getFat(),
                food.getFiber(),
                food.getIron(),
                food.getCalcium(),
                food.getSodium(),
                food.getPotassium()
        };

        for (int i = 0; i < values.length; i++) {

            if (values[i] == null)
                available[i] = false;
            else
                total[i] += values[i] * factor;
        }
    }

    private NutritionResult buildResult(
            String dish,
            double[] total,
            boolean[] available,
            int servings
    ) {

        NutritionResult result =
                new NutritionResult();

        result.setFoodName(
                dish + " - estimated per serving"
        );

        result.setServingSize(1.0);
        result.setServingUnit("serving");

        result.setCalories(value(total, available, 0, servings));
        result.setProtein(value(total, available, 1, servings));
        result.setCarbs(value(total, available, 2, servings));
        result.setFat(value(total, available, 3, servings));
        result.setFiber(value(total, available, 4, servings));
        result.setIron(value(total, available, 5, servings));
        result.setCalcium(value(total, available, 6, servings));
        result.setSodium(value(total, available, 7, servings));
        result.setPotassium(value(total, available, 8, servings));

        result.setSourceType("COMPOSITE_ESTIMATE");
        result.setSource(
                "USDA FoodData Central ingredients"
        );

        result.setVerified(false);
        result.setEstimated(true);

        return result;
    }

    private Double value(
            double[] total,
            boolean[] available,
            int index,
            int servings
    ) {

        return available[index]
                ? round(total[index] / servings)
                : null;
    }

    // =====================================================
    // DIRECT NUTRITION RESPONSE
    // =====================================================

    private String formatNutrition(
            NutritionResult food,
            String nutrient
    ) {

        if (nutrient != null) {

            Double value =
                    nutrientValue(food, nutrient);

            return food.getFoodName()
                    + "\n"
                    + nutrient + ": "
                    + (value == null
                    ? "N/A"
                    : number(value)
                      + " "
                      + unit(nutrient))
                    + " per "
                    + number(food.getServingSize())
                    + " g"
                    + "\nSource: "
                    + food.getSource()
                    + "\nFDC ID: "
                    + food.getSourceId();
        }

        return food.getFoodName()
                + "\nPer "
                + number(food.getServingSize())
                + " g:"
                + "\nCalories: " + show(food.getCalories(), "kcal")
                + "\nProtein: " + show(food.getProtein(), "g")
                + "\nCarbohydrates: " + show(food.getCarbs(), "g")
                + "\nFat: " + show(food.getFat(), "g")
                + "\nFiber: " + show(food.getFiber(), "g")
                + "\nSource: " + food.getSource()
                + "\nFDC ID: " + food.getSourceId();
    }

    private String requestedNutrient(String text) {

        String value = normalize(text);

        if (value.contains("calorie")
                || value.contains("kcal"))
            return "Calories";

        if (value.contains("protein"))
            return "Protein";

        if (value.contains("carb"))
            return "Carbohydrates";

        if (value.contains("fiber")
                || value.contains("fibre"))
            return "Fiber";

        if (value.contains("fat"))
            return "Fat";

        if (value.contains("sodium"))
            return "Sodium";

        if (value.contains("potassium"))
            return "Potassium";

        if (value.contains("calcium"))
            return "Calcium";

        if (value.contains("iron"))
            return "Iron";

        return null;
    }

    private Double nutrientValue(
            NutritionResult food,
            String nutrient
    ) {

        return switch (nutrient) {
            case "Calories" -> food.getCalories();
            case "Protein" -> food.getProtein();
            case "Carbohydrates" -> food.getCarbs();
            case "Fiber" -> food.getFiber();
            case "Fat" -> food.getFat();
            case "Sodium" -> food.getSodium();
            case "Potassium" -> food.getPotassium();
            case "Calcium" -> food.getCalcium();
            case "Iron" -> food.getIron();
            default -> null;
        };
    }

    // =====================================================
    // CHAT CONTEXT
    // =====================================================

    private Context findPending(String userId) {

        List<Map<String, String>> history =
                memory.getHistory(userId);

        for (int i = history.size() - 1; i >= 0; i--) {

            Map<String, String> item =
                    history.get(i);

            String text =
                    item.get("content");

            if (!"assistant".equals(item.get("role"))
                    || text == null)
                continue;

            // Finished calculation/direct lookup
            if (text.startsWith("Estimated nutrition per serving")
                    || text.contains("FDC ID:"))
                return null;

            if (!isIngredientPrompt(text))
                continue;

            String dish = null;
            StringBuilder details =
                    new StringBuilder();

            // Find original user question
            for (int j = i - 1; j >= 0; j--) {

                Map<String, String> old =
                        history.get(j);

                if ("user".equals(old.get("role"))) {
                    dish = dishName(old.get("content"));
                    break;
                }
            }

            // Collect ingredient replies after prompt
            for (int j = i + 1; j < history.size(); j++) {

                Map<String, String> later =
                        history.get(j);

                if ("user".equals(later.get("role"))
                        && hasDetails(later.get("content"))) {

                    if (details.length() > 0)
                        details.append(", ");

                    details.append(
                            later.get("content")
                    );
                }
            }

            return dish == null
                    ? null
                    : new Context(
                    dish,
                    details.toString()
            );
        }

        return null;
    }

    private boolean isIngredientPrompt(String text) {

        return text.startsWith("To estimate ")
                || text.startsWith(
                "I couldn't find a reliable USDA match"
        );
    }

    // =====================================================
    // QUESTION DETECTION
    // =====================================================

    private boolean isHomemade(String text) {

        String value =
                normalize(text);

        return value.contains("homemade")
                || value.contains("home made")
                || value.contains("home-made")
                || value.contains("my recipe")
                || value.contains("i made")
                || value.contains("i cooked")
                || value.contains("i prepared");
    }

    private String dishName(String message) {

        Matcher matcher =
                FOOD_QUERY.matcher(message);

        String dish = null;

        if (matcher.find()) {
            dish = matcher.group(1);
        } else {

            matcher =
                    FOOD_HAVE.matcher(message);

            if (matcher.find())
                dish = matcher.group(1);
        }

        if (dish == null)
            return null;

        dish = normalize(dish)
                .replaceAll(
                        "\\b(?:my|the|a|an|homemade|home made|home-made)\\b",
                        " "
                )
                .replaceAll("\\s+", " ")
                .trim();

        return dish.isBlank()
                ? null
                : dish;
    }

    private boolean hasDetails(String text) {

        return text != null
                && (INGREDIENT.matcher(text).find()
                || SERVINGS.matcher(text).find());
    }

    // =====================================================
    // PROMPTS + HELPERS
    // =====================================================

    private ChatResponse prompt(
            String dish,
            boolean noUsdaMatch
    ) {

        String start = noUsdaMatch
                ? "I couldn't find a reliable USDA match for "
                  + dish
                  + ". "
                : "";

        return new ChatResponse(
                start
                        + "To estimate "
                        + dish
                        + ", send ingredient amounts in grams "
                        + "and the number of servings. "
                        + "Example: 100 g rice, 30 g dal, "
                        + "5 g oil, makes 4 servings."
        );
    }

    private void save(
            String userId,
            String message,
            ChatResponse response
    ) {

        memory.addMessage(
                userId,
                "user",
                message
        );

        memory.addMessage(
                userId,
                "assistant",
                response.getReply()
        );
    }

    private String normalize(String value) {

        return value == null
                ? ""
                : value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' -]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String show(
            Double value,
            String unit
    ) {

        return value == null
                ? "N/A"
                : number(value) + " " + unit;
    }

    private String unit(String nutrient) {

        return switch (nutrient) {
            case "Calories" -> "kcal";
            case "Sodium",
                 "Potassium",
                 "Calcium",
                 "Iron" -> "mg";
            default -> "g";
        };
    }

    private String number(double value) {

        double rounded =
                Math.round(value * 100.0) / 100.0;

        return rounded == Math.rint(rounded)
                ? String.valueOf((long) rounded)
                : String.valueOf(rounded);
    }

    private Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Ingredient(
            String name,
            double grams
    ) {}

    private record Context(
            String dish,
            String details
    ) {}
}