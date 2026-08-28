package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OpenFoodFactsProvider implements NutritionProvider {

    private final RestClient productClient;
    private final RestClient searchClient;

    public OpenFoodFactsProvider() {

        productClient = RestClient.builder()
                .baseUrl("https://world.openfoodfacts.org")
                .defaultHeader(
                        "User-Agent",
                        "NutriVerse/1.0 Academic Project"
                )
                .build();

        searchClient = RestClient.builder()
                .baseUrl("https://search.openfoodfacts.org")
                .defaultHeader(
                        "User-Agent",
                        "NutriVerse/1.0 Academic Project"
                )
                .build();
    }


    // =========================================================
    // SEARCH BY NAME
    // =========================================================

    @Override
    public List<NutritionResult> search(String query) {

        List<NutritionResult> results = new ArrayList<>();

        if (query == null || query.isBlank()) {
            return results;
        }

        try {

            Map response = searchClient
                    .post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "q", query,
                            "page_size", 10,
                            "page", 1
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return results;
            }

            Object hitsObject = response.get("hits");

            if (!(hitsObject instanceof List<?> hits)) {
                return results;
            }

            for (Object object : hits) {

                if (!(object instanceof Map<?, ?> hit)) {
                    continue;
                }

                Map<?, ?> product = hit;

                Object sourceObject = hit.get("_source");

                if (sourceObject instanceof Map<?, ?> source) {
                    product = source;
                }

                NutritionResult result =
                        convertProduct(product);

                if (result != null) {
                    results.add(result);
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "Open Food Facts search failed: "
                            + e.getMessage()
            );
        }

        return results;
    }


    // =========================================================
    // FIND EXACT PRODUCT BY BARCODE
    // =========================================================

    @Override
    public NutritionResult findByBarcode(String barcode) {

        return fetchProduct(barcode);
    }


    // =========================================================
    // FIND EXACT PRODUCT BY SOURCE ID
    // =========================================================

    @Override
    public NutritionResult findBySourceId(String sourceId) {

        return fetchProduct(sourceId);
    }


    // =========================================================
    // FETCH PRODUCT
    // =========================================================

    private NutritionResult fetchProduct(String code) {

        if (code == null || code.isBlank()) {
            return null;
        }

        try {

            Map response = productClient
                    .get()
                    .uri(
                            "/api/v2/product/{code}.json",
                            code
                    )
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return null;
            }

            Object productObject =
                    response.get("product");

            if (!(productObject instanceof Map<?, ?> product)) {
                return null;
            }

            return convertProduct(product);

        } catch (Exception e) {

            System.out.println(
                    "Open Food Facts product lookup failed: "
                            + e.getMessage()
            );

            return null;
        }
    }


    // =========================================================
    // CONVERT PRODUCT → NUTRITION RESULT
    // =========================================================

    private NutritionResult convertProduct(
            Map<?, ?> product
    ) {

        String name = text(
                product.get("product_name")
        );

        if (name == null || name.isBlank()) {
            return null;
        }

        Object nutrientsObject =
                product.get("nutriments");

        if (!(nutrientsObject instanceof Map<?, ?> nutrients)) {
            return null;
        }

        NutritionResult result =
                new NutritionResult();


        String brand =
                text(product.get("brands"));

        if (brand != null && !brand.isBlank()) {

            result.setFoodName(
                    brand + " " + name
            );

        } else {

            result.setFoodName(name);
        }


        result.setCalories(
                number(nutrients.get("energy-kcal_100g"))
        );

        result.setProtein(
                number(nutrients.get("proteins_100g"))
        );

        result.setCarbs(
                number(nutrients.get("carbohydrates_100g"))
        );

        result.setFat(
                number(nutrients.get("fat_100g"))
        );

        result.setFiber(
                number(nutrients.get("fiber_100g"))
        );

        result.setIron(
                number(nutrients.get("iron_100g"))
        );

        result.setCalcium(
                number(nutrients.get("calcium_100g"))
        );

        result.setSodium(
                number(nutrients.get("sodium_100g"))
        );

        result.setPotassium(
                number(nutrients.get("potassium_100g"))
        );


        // Values currently represent per 100 grams
        result.setServingSize(100.0);
        result.setServingUnit("g");


        result.setSourceType(
                "PRODUCT_DATABASE"
        );

        result.setSource(
                "Open Food Facts"
        );

        result.setSourceId(
                text(product.get("code"))
        );

        result.setVerified(false);
        result.setEstimated(false);

        return result;
    }


    // =========================================================
    // HELPERS
    // =========================================================

    private String text(Object value) {

        return value == null
                ? null
                : value.toString();
    }


    private Double number(Object value) {

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value == null) {
            return null;
        }

        try {
            return Double.parseDouble(
                    value.toString()
            );

        } catch (NumberFormatException e) {
            return null;
        }
    }


    @Override
    public String getProviderName() {
        return "Open Food Facts";
    }
}