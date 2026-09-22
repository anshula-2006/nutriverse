package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class NutritionLookupService {

    private static final Logger log =
            LoggerFactory.getLogger(NutritionLookupService.class);

    private static final int MAX_RESULTS = 20;

    private final UsdaFoodDataProvider usda;
    private final OpenFoodFactsProvider off;

    public NutritionLookupService(
            UsdaFoodDataProvider usda,
            OpenFoodFactsProvider off
    ) {
        this.usda = usda;
        this.off = off;
    }

    // ---------------------------------------------------------
    // SEARCH
    // ---------------------------------------------------------

    public List<NutritionResult> search(String query) {

        if (query == null || query.isBlank())
            return List.of();

        if (query.length() > 200)
            throw new IllegalArgumentException(
                    "Search query is too long"
            );

        String searchQuery =
                normalizeRegionalName(query.trim());

        List<NutritionResult> results =
                new ArrayList<>();

        boolean providerFailed = false;

        for (NutritionProvider provider :
                List.of(usda, off)) {

            try {
                List<NutritionResult> found =
                        provider.search(searchQuery);

                if (found == null) {
                    providerFailed = true;
                    continue;
                }

                results.addAll(found);

            } catch (RuntimeException e) {

                providerFailed = true;

                log.warn(
                        "{} search failed: {}",
                        providerName(provider),
                        e.getClass().getSimpleName()
                );
            }
        }

        /*
         * At least one provider failed and the other provider
         * also gave us no usable result.
         *
         * Returning [] here would incorrectly look like
         * "food not found" instead of "provider unavailable".
         */
        if (results.isEmpty() && providerFailed) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Nutrition search is temporarily unavailable. "
                            + "Please try again shortly."
            );
        }

        return rankAndDeduplicate(
                results,
                searchQuery
        );
    }

    // ---------------------------------------------------------
    // BARCODE
    // ---------------------------------------------------------

    public NutritionResult findByBarcode(
            String barcode
    ) {

        if (barcode == null ||
                barcode.isBlank()) {
            return null;
        }

        return off.findByBarcode(
                barcode.trim()
        );
    }

    // ---------------------------------------------------------
    // EXACT SOURCE LOOKUP
    // ---------------------------------------------------------

    public NutritionResult findBySourceId(
            String source,
            String sourceId
    ) {

        if (source == null ||
                sourceId == null ||
                sourceId.isBlank()) {
            return null;
        }

        NutritionProvider provider =
                providerFor(source.trim());

        if (provider == null)
            return null;

        NutritionResult result =
                provider.findBySourceId(
                        sourceId.trim()
                );

        if (result == null)
            return null;

        if (!sourceId.trim()
                .equals(result.getSourceId())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nutrition provider returned "
                            + "a different food."
            );
        }

        String providerName =
                provider.getProviderName();

        if (providerName != null &&
                result.getSource() != null &&
                !providerName.equals(
                        result.getSource())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nutrition provider returned "
                            + "an unexpected source."
            );
        }

        return result;
    }

    // ---------------------------------------------------------
    // MERGE + RANK
    // ---------------------------------------------------------

    private List<NutritionResult> rankAndDeduplicate(
            List<NutritionResult> foods,
            String query
    ) {

        Map<String, NutritionResult> unique =
                new LinkedHashMap<>();

        int unknown = 0;

        for (NutritionResult food : foods) {

            if (food == null)
                continue;

            String key;

            if (food.getSource() != null &&
                    food.getSourceId() != null) {

                key = food.getSource()
                        + ":"
                        + food.getSourceId();

            } else {

                // Keeps mocked/provider results that have no IDs.
                key = "UNKNOWN:" + unknown++;
            }

            unique.putIfAbsent(
                    key,
                    food
            );
        }
        return unique.values()
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                (NutritionResult food) ->
                                        relevance(food, query)
                        ).reversed()
                )
                .limit(MAX_RESULTS)
                .toList();

    }

    private int relevance(
            NutritionResult food,
            String query
    ) {

        if (food.getFoodName() == null)
            return 0;

        String foodName =
                normalize(food.getFoodName());

        String search =
                normalize(query);

        int score = 0;

        if (foodName.equals(search))
            score += 20;
        else if (foodName.contains(search))
            score += 10;

        for (String word :
                search.split(" ")) {

            if (word.length() >= 3 &&
                    foodName.contains(word)) {
                score += 2;
            }
        }

        // Small preference for authoritative USDA records.
        if (food.isVerified() &&
                "USDA FoodData Central"
                        .equals(food.getSource())) {
            score += 3;
        }

        return score;
    }

    // ---------------------------------------------------------
    // REGIONAL FOOD NAMES
    // ---------------------------------------------------------

    private String normalizeRegionalName(
            String query
    ) {

        String result =
                query.toLowerCase(Locale.ROOT);

        result = result.replace(
                "green gram",
                "mung beans"
        );

        result = result.replace(
                "moong",
                "mung"
        );

        result = result.replace(
                "toor",
                "pigeon pea"
        );

        result = result.replace(
                "rajma",
                "kidney beans"
        );

        result = result.replace(
                "chana",
                "chickpea"
        );

        result = result.replace(
                "curd",
                "yogurt"
        );

        return result.trim();
    }

    // ---------------------------------------------------------
    // PROVIDER RESOLUTION
    // ---------------------------------------------------------

    private NutritionProvider providerFor(
            String source
    ) {

        if ("USDA".equalsIgnoreCase(source)
                || "USDA FoodData Central"
                .equalsIgnoreCase(source)) {

            return usda;
        }

        if ("OFF".equalsIgnoreCase(source)
                || "Open Food Facts"
                .equalsIgnoreCase(source)) {

            return off;
        }

        return null;
    }

    private String providerName(
            NutritionProvider provider
    ) {

        String name =
                provider.getProviderName();

        return name == null
                ? provider.getClass()
                .getSimpleName()
                : name;
    }

    private String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                .replace("-", " ")
                .replaceAll(
                        "[^a-z0-9 ]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }
}