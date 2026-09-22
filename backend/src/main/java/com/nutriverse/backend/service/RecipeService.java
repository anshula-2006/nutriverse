package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class RecipeService {

    private static final Pattern GRAMS = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\h*g\\h+([^,;\\n]+)"
    );

    private static final Pattern HEALTH_CLAIM = Pattern.compile(
            "(?i)\\b(?:high[- ]?protein|protein[- ]?rich|good source of protein|healthy fats?|"
                    + "low[- ]?calorie|high[- ]?fiber|iron[- ]?rich|fitness[- ]?focused|"
                    + "weight[- ]?loss friendly|heart healthy|blood sugar friendly)\\b"
    );

    private static final Pattern SOURCE_CLAIM = Pattern.compile(
            "(?i)\\b(?:USDA|FoodData Central|Open Food Facts|FDA|NIH|ICMR|NIN|verified by)\\b"
    );

    private final ChatMemory memory;
    private final NutritionLookupService lookup;

    public RecipeService(ChatMemory memory, NutritionLookupService lookup) {
        this.memory = memory;
        this.lookup = lookup;
    }

    public boolean canAnswerNutrition(String userId, String message) {
        return nutrient(message) != null
                && findRecipe(memory.getHistory(userId), message) != null;
    }

    public String nutritionReply(String userId, String message) {
        Recipe recipe = findRecipe(memory.getHistory(userId), message);
        String nutrient = nutrient(message);

        if (recipe == null || nutrient == null)
            return "I couldn't find a recent recipe to calculate.";

        var matcher = GRAMS.matcher(ingredientBlock(recipe.text()));
        List<String> evidence = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        double total = 0;

        while (matcher.find()) {
            double grams = Double.parseDouble(matcher.group(1));
            String ingredient = clean(matcher.group(2));
            NutritionResult food = verifiedIngredient(ingredient);
            Double value = food == null ? null : value(food, nutrient);

            if (food == null || value == null || value < 0 || !Double.isFinite(value)) {
                missing.add(number(grams) + " g " + ingredient);
                continue;
            }

            total += value * grams / food.getServingSize();
            evidence.add(ingredient + " → USDA FDC " + food.getSourceId());
        }

        if (evidence.isEmpty())
            return "I found " + recipe.title()
                    + ", but I couldn't verify enough gram-based ingredients to calculate it reliably.";

        String unit = nutrient.equals("Calories") ? "kcal" : "g";
        StringBuilder out = new StringBuilder();

        out.append("Estimated ")
                .append(nutrient.toLowerCase(Locale.ROOT))
                .append(" for the whole ")
                .append(recipe.title())
                .append(": ");

        if (!missing.isEmpty())
            out.append("at least ");

        out.append(round(total)).append(" ").append(unit);

        if (!missing.isEmpty())
            out.append("\nNot counted: ").append(String.join("; ", missing));

        out.append("\n\nEvidence: ").append(String.join("; ", evidence))
                .append("\nSource: USDA FoodData Central ingredient records.")
                .append("\nThis is an ingredient-based estimate, not a USDA recipe record.");

        return out.toString();
    }

    public String guardGeneratedRecipe(String reply) {
        if (reply == null || reply.isBlank())
            return "I couldn't generate that recipe.";

        StringBuilder safe = new StringBuilder();

        for (String line : reply.split("\\R")) {
            if (SOURCE_CLAIM.matcher(line).find()) continue;
            if (HEALTH_CLAIM.matcher(line).find()) continue;
            safe.append(line).append("\n");
        }

        String result = safe.toString().trim();
        return result.isBlank()
                ? "I couldn't safely format that recipe. Please try again."
                : result;
    }

    private Recipe findRecipe(List<Map<String, String>> history, String message) {
        if (history == null) return null;

        String query = normalize(message);
        boolean latest = query.contains("that recipe")
                || query.contains("this recipe")
                || query.contains("the recipe");

        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> item = history.get(i);
            String text = item.get("content");

            if (!"assistant".equals(item.get("role"))
                    || text == null
                    || !normalize(text).contains("ingredients"))
                continue;

            String title = title(text);

            if (title != null && (latest || titleMatches(title, message)))
                return new Recipe(title, text);
        }

        return null;
    }

    private String title(String recipe) {
        for (String raw : recipe.split("\\R")) {
            String line = raw.strip();

            if (!line.startsWith("**")) continue;

            String title = line.replace("*", "").replace(":", "").trim();
            String n = normalize(title);

            if (!title.isBlank()
                    && title.length() <= 70
                    && !Set.of("ingredients", "steps", "method", "instructions").contains(n))
                return title;
        }

        return null;
    }

    private boolean titleMatches(String title, String message) {
        String q = normalize(message);
        String t = normalize(title);

        if (q.contains(t)) return true;

        for (String word : t.split(" "))
            if (word.length() >= 5 && q.contains(word))
                return true;

        return false;
    }

    private String ingredientBlock(String recipe) {
        String[] parts = recipe.split("(?i)\\bIngredients\\b\\s*:?", 2);
        if (parts.length < 2) return "";

        return parts[1]
                .split("(?i)\\b(?:Cooking time|Temperature|Steps|Method|Instructions)\\b", 2)[0]
                .replaceAll("\\([^)]*\\)", "");
    }

    private NutritionResult verifiedIngredient(String ingredient) {
        List<NutritionResult> results = lookup.search(query(ingredient));
        if (results == null) return null;

        for (NutritionResult candidate : results) {
            if (!verified(candidate)) continue;

            NutritionResult exact = lookup.findBySourceId(
                    candidate.getSource(),
                    candidate.getSourceId()
            );

            if (verified(exact)) return exact;
        }

        return null;
    }

    private String query(String ingredient) {
        String v = normalize(ingredient);

        if (v.contains("curd") || v.contains("yogurt")) return "yogurt plain";
        if (v.contains("chicken breast")) return "chicken breast raw";
        if (v.contains("salmon")) return "salmon raw";
        if (v.contains("moong") || v.contains("mung")) return "mung beans mature seeds cooked";
        if (v.contains("toor") || v.contains("pigeon pea")) return "pigeon peas mature seeds cooked";
        if (v.contains("lentil")) return "lentils mature seeds cooked";
        if (v.contains("chickpea")) return "chickpeas mature seeds cooked";
        if (v.contains("rice")) return v.contains("raw") || v.contains("dry")
                ? "rice white long grain raw" : "rice white long grain cooked";
        if (v.contains("tomato")) return "tomatoes red ripe raw";
        if (v.contains("onion")) return "onions raw";
        if (v.contains("spinach")) return "spinach raw";
        if (v.contains("olive oil")) return "oil olive";
        if (v.matches(".*\\boil\\b.*")) return "vegetable oil";

        return ingredient;
    }

    private boolean verified(NutritionResult f) {
        return f != null
                && "USDA FoodData Central".equals(f.getSource())
                && "AUTHORITATIVE_DATABASE".equals(f.getSourceType())
                && f.isVerified()
                && !f.isEstimated()
                && f.getSourceId() != null
                && f.getSourceId().matches("\\d+")
                && f.getServingSize() != null
                && f.getServingSize() > 0
                && "g".equals(f.getServingUnit());
    }

    private String nutrient(String message) {
        String v = normalize(message);

        if (v.contains("calorie") || v.contains("kcal")) return "Calories";
        if (v.contains("protein")) return "Protein";
        if (v.contains("carb")) return "Carbohydrates";
        if (v.contains("fiber") || v.contains("fibre")) return "Fiber";
        if (v.contains("fat")) return "Fat";

        return null;
    }

    private Double value(NutritionResult f, String nutrient) {
        return switch (nutrient) {
            case "Calories" -> f.getCalories();
            case "Protein" -> f.getProtein();
            case "Carbohydrates" -> f.getCarbs();
            case "Fiber" -> f.getFiber();
            case "Fat" -> f.getFat();
            default -> null;
        };
    }

    private String clean(String value) {
        return value.replaceAll("\\([^)]*\\)", "")
                .replaceFirst("^[•*-]\\s*", "")
                .trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace('\u2011', '-')
                .replace('\u2019', '\'')
                .replaceAll("[^a-z0-9' -]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private record Recipe(String title, String text) {}
}