package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class UsdaFoodDataProvider implements NutritionProvider {

    private static final Logger log =
            LoggerFactory.getLogger(UsdaFoodDataProvider.class);

    private final RestClient client;
    private final String apiKey;
    private final DietaryComplianceEngine dietaryEngine;

    @Autowired
    public UsdaFoodDataProvider(
            @Value("${usda.api.url}") String apiUrl,
            @Value("${usda.api.key}") String apiKey,
            DietaryComplianceEngine dietaryEngine
    ) {
        this(
                createClient(apiUrl),
                apiKey,
                dietaryEngine
        );
    }

    // Used by live USDA tests
    UsdaFoodDataProvider(
            String apiUrl,
            String apiKey
    ) {
        this(
                createClient(apiUrl),
                apiKey,
                new DietaryComplianceEngine()
        );
    }

    // Used by mocked unit tests
    UsdaFoodDataProvider(
            RestClient client,
            String apiKey
    ) {
        this(
                client,
                apiKey,
                new DietaryComplianceEngine()
        );
    }

    private UsdaFoodDataProvider(
            RestClient client,
            String apiKey,
            DietaryComplianceEngine dietaryEngine
    ) {
        this.client = client;
        this.apiKey = apiKey;
        this.dietaryEngine = dietaryEngine;
    }

    private static RestClient createClient(String url) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }

    @Override
    public List<NutritionResult> search(String query) {

        if (query == null || query.isBlank())
            return List.of();

        try {
            Map<?, ?> response = client.get()
                    .uri(uri -> uri
                            .path("/foods/search")
                            .queryParam("api_key", "{key}")
                            .queryParam("query", "{query}")
                            .queryParam("pageSize", 25)
                            .build(apiKey, query.trim())
                    )
                    .retrieve()
                    .body(Map.class);

            if (response == null ||
                    !(response.get("foods") instanceof List<?> foods)) {
                throw new IllegalStateException(
                        "Missing USDA search results"
                );
            }

            List<NutritionResult> results =
                    new ArrayList<>();

            Set<String> ids =
                    new HashSet<>();

            for (Object object : foods) {

                if (!(object instanceof Map<?, ?> food))
                    continue;

                NutritionResult result =
                        convertFood(food);

                if (result != null &&
                        ids.add(result.getSourceId())) {
                    results.add(result);
                }
            }

            return results;

        } catch (Exception e) {
            throw unavailable("search", e);
        }
    }

    @Override
    public NutritionResult findBySourceId(String sourceId) {

        Long id = positiveLong(sourceId);

        if (id == null)
            return null;

        try {
            Map<?, ?> food = client.get()
                    .uri(uri -> uri
                            .path("/food/{id}")
                            .queryParam("api_key", "{key}")
                            .build(id, apiKey)
                    )
                    .retrieve()
                    .body(Map.class);

            if (food == null)
                return null;

            Long returnedId =
                    positiveLong(text(food.get("fdcId")));

            if (!id.equals(returnedId)) {
                throw new IllegalStateException(
                        "USDA returned a different food identifier"
                );
            }

            return convertFood(food);

        } catch (RestClientResponseException e) {

            if (e.getStatusCode().value() == 404)
                return null;

            throw unavailable("lookup", e);

        } catch (Exception e) {
            throw unavailable("lookup", e);
        }
    }

    private NutritionResult convertFood(
            Map<?, ?> food
    ) {

        String name =
                text(food.get("description"));

        String sourceId =
                text(food.get("fdcId"));

        String dataType =
                text(food.get("dataType"));

        if (name == null ||
                positiveLong(sourceId) == null ||
                !supportedType(dataType) ||
                !(food.get("foodNutrients")
                        instanceof List<?> nutrients)) {
            return null;
        }

        NutritionResult result =
                new NutritionResult();

        result.setFoodName(name);

        if ("Foundation".equalsIgnoreCase(dataType)) {

            result.setCalories(first(
                    nutrient(
                            nutrients,
                            "958",
                            "Energy (Atwater Specific Factors)",
                            "kcal"
                    ),
                    nutrient(
                            nutrients,
                            "957",
                            "Energy (Atwater General Factors)",
                            "kcal"
                    ),
                    nutrient(
                            nutrients,
                            "208",
                            "Energy",
                            "kcal"
                    )
            ));

        } else {

            result.setCalories(first(
                    nutrient(
                            nutrients,
                            "208",
                            "Energy",
                            "kcal"
                    ),
                    nutrient(
                            nutrients,
                            "958",
                            "Energy (Atwater Specific Factors)",
                            "kcal"
                    ),
                    nutrient(
                            nutrients,
                            "957",
                            "Energy (Atwater General Factors)",
                            "kcal"
                    )
            ));
        }

        result.setProtein(
                nutrient(
                        nutrients,
                        "203",
                        "Protein",
                        "g"
                )
        );

        result.setFat(
                nutrient(
                        nutrients,
                        "204",
                        "Total lipid (fat)",
                        "g"
                )
        );

        result.setCarbs(first(
                nutrient(
                        nutrients,
                        "205",
                        "Carbohydrate, by difference",
                        "g"
                ),
                nutrient(
                        nutrients,
                        "205.2",
                        "Carbohydrate, by summation",
                        "g"
                )
        ));

        result.setFiber(first(
                nutrient(
                        nutrients,
                        "291",
                        "Fiber, total dietary",
                        "g"
                ),
                nutrient(
                        nutrients,
                        "293",
                        "Total dietary fiber (AOAC 2011.25)",
                        "g"
                )
        ));

        result.setIron(
                nutrient(
                        nutrients,
                        "303",
                        "Iron, Fe",
                        "mg"
                )
        );

        result.setCalcium(
                nutrient(
                        nutrients,
                        "301",
                        "Calcium, Ca",
                        "mg"
                )
        );

        result.setSodium(
                nutrient(
                        nutrients,
                        "307",
                        "Sodium, Na",
                        "mg"
                )
        );

        result.setPotassium(
                nutrient(
                        nutrients,
                        "306",
                        "Potassium, K",
                        "mg"
                )
        );

        if (!hasUsefulNutrition(result))
            return null;

        result.setServingSize(100.0);
        result.setServingUnit("g");

        result.setSource("USDA FoodData Central");
        result.setSourceType("AUTHORITATIVE_DATABASE");
        result.setSourceId(sourceId);
        result.setDataType(dataType);

        result.setVerified(true);
        result.setEstimated(false);

        // USDA usually does not provide ingredient lists.
        // Only obvious conflicts from the food name are detected.
        dietaryEngine.applyNameOnly(result);

        return result;
    }

    private Double nutrient(
            List<?> nutrients,
            String expectedNumber,
            String expectedName,
            String expectedUnit
    ) {

        for (Object object : nutrients) {

            if (!(object instanceof Map<?, ?> row))
                continue;

            Map<?, ?> details = row;

            if (row.get("nutrient")
                    instanceof Map<?, ?> nested) {
                details = nested;
            }

            String number =
                    firstText(
                            details.get("number"),
                            row.get("nutrientNumber")
                    );

            String name =
                    firstText(
                            details.get("name"),
                            row.get("nutrientName")
                    );

            String unit =
                    firstText(
                            details.get("unitName"),
                            row.get("unitName")
                    );

            boolean matches =
                    expectedNumber.equals(number)
                            || (number == null
                            && expectedName.equalsIgnoreCase(name));

            if (!matches ||
                    unit == null ||
                    !expectedUnit.equalsIgnoreCase(unit)) {
                continue;
            }

            Double amount =
                    number(row.get("amount"));

            if (amount != null)
                return amount;

            Double value =
                    number(row.get("value"));

            if (value != null)
                return value;
        }

        return null;
    }

    private boolean supportedType(String dataType) {
        return "Foundation".equalsIgnoreCase(dataType)
                || "SR Legacy".equalsIgnoreCase(dataType)
                || "Survey (FNDDS)".equalsIgnoreCase(dataType);
    }

    private boolean hasUsefulNutrition(
            NutritionResult result
    ) {
        return result.getCalories() != null
                || result.getProtein() != null
                || result.getCarbs() != null
                || result.getFat() != null;
    }

    private Double first(Double... values) {

        for (Double value : values) {
            if (value != null)
                return value;
        }

        return null;
    }

    private String firstText(Object... values) {

        for (Object value : values) {

            String result =
                    text(value);

            if (result != null)
                return result;
        }

        return null;
    }

    private String text(Object value) {

        if (value == null)
            return null;

        String result =
                value.toString().trim();

        return result.isEmpty()
                ? null
                : result;
    }

    private Double number(Object value) {

        try {
            if (value == null)
                return null;

            double result =
                    value instanceof Number n
                            ? n.doubleValue()
                            : Double.parseDouble(
                            value.toString()
                    );

            return Double.isFinite(result)
                    && result >= 0
                    ? result
                    : null;

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long positiveLong(String value) {

        try {
            long result =
                    Long.parseLong(
                            value == null
                                    ? ""
                                    : value.trim()
                    );

            return result > 0
                    ? result
                    : null;

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ResponseStatusException unavailable(
            String operation,
            Exception error
    ) {

        String status =
                error instanceof RestClientResponseException response
                        ? String.valueOf(
                        response.getStatusCode().value()
                )
                        : "unavailable";

        log.warn(
                "USDA {} failed: status={} type={}",
                operation,
                status,
                error.getClass().getSimpleName()
        );

        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "USDA FoodData Central is unavailable. Please try again shortly."
        );
    }

    @Override
    public String getProviderName() {
        return "USDA FoodData Central";
    }
}