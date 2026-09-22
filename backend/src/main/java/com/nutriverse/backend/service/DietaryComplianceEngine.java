package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class DietaryComplianceEngine {

    private static final Set<String> GLUTEN =
            Set.of("wheat", "barley", "rye", "malt",
                    "semolina", "maida", "bread", "breadcrumbs");

    private static final Set<String> JAIN =
            Set.of("onion", "garlic", "potato", "carrot",
                    "beetroot", "radish", "meat", "fish",
                    "egg", "gelatin");

    private static final Set<String> HALAL =
            Set.of("pork", "bacon", "ham", "lard",
                    "alcohol", "wine");

    private static final Set<String> KOSHER =
            Set.of("pork", "shellfish", "shrimp", "lobster");

    public void applyNameOnly(NutritionResult food) {

        String name = food.getFoodName();

        food.setGlutenStatus(checkName(name, GLUTEN));
        food.setJainStatus(checkName(name, JAIN));
        food.setHalalStatus(checkName(name, HALAL));
        food.setKosherStatus(checkName(name, KOSHER));
    }

    private String checkName(
            String name,
            Set<String> blocked
    ) {

        if (name == null || name.isBlank())
            return "UNKNOWN";

        String text =
                " " + name
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z ]", " ")
                        .replaceAll("\\s+", " ")
                        .trim()
                        + " ";

        boolean conflict =
                blocked.stream()
                        .anyMatch(word ->
                                text.contains(" " + word + " "));

        return conflict ? "CONFLICT" : "UNKNOWN";
    }

    public void apply(NutritionResult food) {

        String ingredients = food.getIngredients();

        food.setGlutenStatus(check(ingredients, GLUTEN));
        food.setJainStatus(check(ingredients, JAIN));
        food.setHalalStatus(check(ingredients, HALAL));
        food.setKosherStatus(check(ingredients, KOSHER));
    }

    private String check(
            String ingredients,
            Set<String> blocked
    ) {

        if (ingredients == null || ingredients.isBlank())
            return "UNKNOWN";

        String text =
                " " + ingredients
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z ]", " ")
                        .replaceAll("\\s+", " ")
                        .trim()
                        + " ";

        boolean conflict =
                blocked.stream()
                        .anyMatch(word ->
                                text.contains(" " + word + " "));

        return conflict ? "CONFLICT" : "PASS";
    }
}