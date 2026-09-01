package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class UsdaFoodDataProvider implements NutritionProvider {

    private static final Logger logger =
            LoggerFactory.getLogger(UsdaFoodDataProvider.class);

    private final RestClient client;
    private final String apiKey;

    public UsdaFoodDataProvider(
            @Value("${usda.api.url}") String apiUrl,
            @Value("${usda.api.key}") String apiKey) {

        this.client = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public List<NutritionResult> search(String query) {

        List<NutritionResult> results = new ArrayList<>();

        if (query == null || query.isBlank()) {
            return results;
        }

        try {
            Map<?, ?> response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/foods/search")
                            .queryParam("api_key", apiKey)
                            .queryParam("query", query.trim())
                            .queryParam("pageSize", 10)
                            .queryParam(
                                    "dataType",
                                    "Foundation,SR Legacy,Survey (FNDDS)"
                            )
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null ||
                    !(response.get("foods") instanceof List<?> foods)) {
                return results;
            }

            for (Object object : foods) {
                if (!(object instanceof Map<?, ?> food)) {
                    continue;
                }

                NutritionResult result = convertFood(food);
                if (result != null) {
                    results.add(result);
                }
            }
        } catch (Exception e) {
            logger.warn(
                    "USDA FoodData Central search failed ({})",
                    e.getClass().getSimpleName()
            );
        }

        return results;
    }

    @Override
    public NutritionResult findBySourceId(String sourceId) {

        Long fdcId = positiveLong(sourceId);
        if (fdcId == null) {
            return null;
        }

        try {
            Map<?, ?> food = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/food/{fdcId}")
                            .queryParam("api_key", apiKey)
                            .build(fdcId))
                    .retrieve()
                    .body(Map.class);

            return food == null ? null : convertFood(food);
        } catch (Exception e) {
            logger.warn(
                    "USDA FoodData Central lookup failed for fdcId {} ({})",
                    fdcId,
                    e.getClass().getSimpleName()
            );
            return null;
        }
    }

    private NutritionResult convertFood(Map<?, ?> food) {

        String description = text(food.get("description"));
        String fdcId = text(food.get("fdcId"));
        String dataType = text(food.get("dataType"));

        if (description == null || fdcId == null ||
                !hasPer100GramValues(food, dataType) ||
                !(food.get("foodNutrients") instanceof List<?> nutrients)) {
            return null;
        }

        NutritionResult result = new NutritionResult();
        result.setFoodName(description);

        if ("Foundation".equalsIgnoreCase(dataType)) {
            result.setCalories(first(
                    nutrient(nutrients, "958", "Energy (Atwater Specific Factors)", "kcal"),
                    nutrient(nutrients, "957", "Energy (Atwater General Factors)", "kcal"),
                    nutrient(nutrients, "208", "Energy", "kcal")
            ));
        } else {
            result.setCalories(first(
                    nutrient(nutrients, "208", "Energy", "kcal"),
                    nutrient(nutrients, "958", "Energy (Atwater Specific Factors)", "kcal"),
                    nutrient(nutrients, "957", "Energy (Atwater General Factors)", "kcal")
            ));
        }

        result.setProtein(nutrient(nutrients, "203", "Protein", "g"));
        result.setFat(nutrient(nutrients, "204", "Total lipid (fat)", "g"));
        result.setCarbs(first(
                nutrient(nutrients, "205", "Carbohydrate, by difference", "g"),
                nutrient(nutrients, "205.2", "Carbohydrate, by summation", "g")
        ));
        result.setFiber(first(
                nutrient(nutrients, "291", "Fiber, total dietary", "g"),
                nutrient(nutrients, "293", "Total dietary fiber (AOAC 2011.25)", "g")
        ));
        result.setIron(nutrient(nutrients, "303", "Iron, Fe", "mg"));
        result.setCalcium(nutrient(nutrients, "301", "Calcium, Ca", "mg"));
        result.setSodium(nutrient(nutrients, "307", "Sodium, Na", "mg"));
        result.setPotassium(nutrient(nutrients, "306", "Potassium, K", "mg"));

        if (!hasUsefulNutrition(result)) {
            return null;
        }

        result.setServingSize(100.0);
        result.setServingUnit("g");
        result.setSource("USDA FoodData Central");
        result.setSourceType("AUTHORITATIVE_DATABASE");
        result.setSourceId(fdcId);
        result.setVerified(true);
        result.setEstimated(false);

        return result;
    }

    private Double nutrient(
            List<?> nutrients,
            String expectedNumber,
            String expectedName,
            String expectedUnit) {

        for (Object object : nutrients) {
            if (!(object instanceof Map<?, ?> row)) {
                continue;
            }

            Map<?, ?> details = row;
            if (row.get("nutrient") instanceof Map<?, ?> nested) {
                details = nested;
            }

            String number = firstText(
                    details.get("number"),
                    row.get("nutrientNumber")
            );
            String name = firstText(
                    details.get("name"),
                    row.get("nutrientName")
            );
            String unit = firstText(
                    details.get("unitName"),
                    row.get("unitName")
            );

            boolean matches = expectedNumber.equals(number) ||
                    (number == null && expectedName.equalsIgnoreCase(name));

            if (matches && expectedUnit.equalsIgnoreCase(unit)) {
                Double amount = number(row.get("amount"));
                return amount != null ? amount : number(row.get("value"));
            }
        }

        return null;
    }

    private boolean hasPer100GramValues(
            Map<?, ?> food,
            String dataType) {

        if ("Foundation".equalsIgnoreCase(dataType) ||
                "SR Legacy".equalsIgnoreCase(dataType) ||
                "Survey (FNDDS)".equalsIgnoreCase(dataType)) {
            return true;
        }

        if (!"Branded".equalsIgnoreCase(dataType)) {
            return false;
        }

        String unit = text(food.get("servingSizeUnit"));
        return "g".equalsIgnoreCase(unit) ||
                "gram".equalsIgnoreCase(unit) ||
                "grams".equalsIgnoreCase(unit) ||
                "GRM".equalsIgnoreCase(unit);
    }

    private boolean hasUsefulNutrition(NutritionResult result) {
        return result.getCalories() != null ||
                result.getProtein() != null ||
                result.getCarbs() != null ||
                result.getFat() != null;
    }

    private Double first(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return value == null ? null : Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getProviderName() {
        return "USDA FoodData Central";
    }
}
