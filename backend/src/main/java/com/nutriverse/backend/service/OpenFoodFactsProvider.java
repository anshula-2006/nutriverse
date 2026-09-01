package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OpenFoodFactsProvider
        implements NutritionProvider {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    OpenFoodFactsProvider.class
            );

    private final RestClient productClient;
    private final RestClient searchClient;


    public OpenFoodFactsProvider() {

        this.productClient =
                RestClient.builder()
                        .baseUrl(
                                "https://world.openfoodfacts.org"
                        )
                        .defaultHeader(
                                "User-Agent",
                                "NutriVerse/1.0 Academic Project"
                        )
                        .build();

        this.searchClient =
                RestClient.builder()
                        .baseUrl(
                                "https://search.openfoodfacts.org"
                        )
                        .defaultHeader(
                                "User-Agent",
                                "NutriVerse/1.0 Academic Project"
                        )
                        .build();
    }


    // =========================================================
    // SEARCH
    // =========================================================

    @Override
    public List<NutritionResult> search(
            String query) {

        List<NutritionResult> results =
                new ArrayList<>();

        if (query == null ||
                query.isBlank()) {

            return results;
        }

        try {

            Map<?, ?> response =
                    searchClient
                            .post()
                            .uri("/search")
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(
                                    Map.of(
                                            "q", query.trim(),
                                            "page_size", 10,
                                            "page", 1
                                    )
                            )
                            .retrieve()
                            .body(Map.class);


            if (response == null) {
                return results;
            }


            Object hitsObject =
                    response.get("hits");

            if (!(hitsObject instanceof List<?> hits)) {
                return results;
            }


            for (Object object : hits) {

                if (!(object instanceof Map<?, ?> hit)) {
                    continue;
                }


                Map<?, ?> product = hit;

                Object sourceObject =
                        hit.get("_source");

                if (sourceObject
                        instanceof Map<?, ?> source) {

                    product = source;
                }


                NutritionResult result =
                        convertProduct(product);

                if (result != null) {
                    results.add(result);
                }
            }


        } catch (Exception e) {

            logger.warn(
                    "Open Food Facts search failed: {}",
                    e.getMessage()
            );
        }


        return results;
    }


    // =========================================================
    // EXACT LOOKUP
    // =========================================================

    @Override
    public NutritionResult findByBarcode(
            String barcode) {

        return fetchProduct(barcode);
    }


    @Override
    public NutritionResult findBySourceId(
            String sourceId) {

        return fetchProduct(sourceId);
    }


    private NutritionResult fetchProduct(
            String code) {

        if (code == null ||
                code.isBlank()) {

            return null;
        }


        try {

            Map<?, ?> response =
                    productClient
                            .get()
                            .uri(
                                    "/api/v2/product/{code}.json",
                                    code.trim()
                            )
                            .retrieve()
                            .body(Map.class);


            if (response == null) {
                return null;
            }


            Object productObject =
                    response.get("product");

            if (!(productObject
                    instanceof Map<?, ?> product)) {

                return null;
            }


            return convertProduct(product);


        } catch (Exception e) {

            logger.warn(
                    "Open Food Facts lookup failed for {}: {}",
                    code,
                    e.getMessage()
            );

            return null;
        }
    }


    // =========================================================
    // PRODUCT → NUTRITION RESULT
    // =========================================================

    private NutritionResult convertProduct(
            Map<?, ?> product) {

        String name =
                text(
                        product.get(
                                "product_name"
                        )
                );


        if (name == null ||
                name.isBlank()) {

            return null;
        }


        Object nutrientsObject =
                product.get("nutriments");

        if (!(nutrientsObject
                instanceof Map<?, ?> nutrients)) {

            return null;
        }


        NutritionResult result =
                new NutritionResult();


        String brand =
                text(
                        product.get("brands")
                );


        if (brand != null &&
                !brand.isBlank()) {

            result.setFoodName(
                    brand + " " + name
            );

        } else {

            result.setFoodName(name);
        }


        // =====================================================
        // MACROS
        // OFF _100g values are normalized.
        // Weight-based macronutrients are in grams.
        // =====================================================

        result.setCalories(
                number(
                        nutrients.get(
                                "energy-kcal_100g"
                        )
                )
        );

        result.setProtein(
                number(
                        nutrients.get(
                                "proteins_100g"
                        )
                )
        );

        result.setCarbs(
                number(
                        nutrients.get(
                                "carbohydrates_100g"
                        )
                )
        );

        result.setFat(
                number(
                        nutrients.get(
                                "fat_100g"
                        )
                )
        );

        result.setFiber(
                number(
                        nutrients.get(
                                "fiber_100g"
                        )
                )
        );


        // =====================================================
        // MICRONUTRIENTS
        //
        // OFF _100g weight values are normalized to grams.
        // NutriVerse stores these four minerals in milligrams.
        // =====================================================

        result.setIron(
                gramsToMilligrams(
                        number(
                                nutrients.get(
                                        "iron_100g"
                                )
                        )
                )
        );

        result.setCalcium(
                gramsToMilligrams(
                        number(
                                nutrients.get(
                                        "calcium_100g"
                                )
                        )
                )
        );

        result.setSodium(
                gramsToMilligrams(
                        number(
                                nutrients.get(
                                        "sodium_100g"
                                )
                        )
                )
        );

        result.setPotassium(
                gramsToMilligrams(
                        number(
                                nutrients.get(
                                        "potassium_100g"
                                )
                        )
                )
        );


        // All values above represent nutrition per 100 g.

        result.setServingSize(100.0);
        result.setServingUnit("g");


        // =====================================================
        // PROVENANCE
        // =====================================================

        result.setSourceType(
                "PRODUCT_DATABASE"
        );

        result.setSource(
                "Open Food Facts"
        );

        result.setSourceId(
                text(
                        product.get("code")
                )
        );


        // OFF is community-maintained,
        // therefore we do not mark its data as authoritative.

        result.setVerified(false);
        result.setEstimated(false);


        return result;
    }


    // =========================================================
    // HELPERS
    // =========================================================

    private Double gramsToMilligrams(
            Double grams) {

        if (grams == null) {
            return null;
        }

        return Math.round(
                grams * 1000.0 * 100.0
        ) / 100.0;
    }


    private String text(
            Object value) {

        return value == null
                ? null
                : value.toString();
    }


    private Double number(
            Object value) {

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