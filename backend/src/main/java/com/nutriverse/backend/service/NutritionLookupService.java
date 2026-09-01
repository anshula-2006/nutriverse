package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NutritionLookupService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    NutritionLookupService.class
            );

    private final UsdaFoodDataProvider usdaProvider;
    private final OpenFoodFactsProvider openFoodFactsProvider;

    public NutritionLookupService(
            UsdaFoodDataProvider usdaProvider,
            OpenFoodFactsProvider openFoodFactsProvider
    ) {
        this.usdaProvider = usdaProvider;
        this.openFoodFactsProvider = openFoodFactsProvider;
    }


    // =========================================================
    // SEARCH BY FOOD / PRODUCT NAME
    // =========================================================

    public List<NutritionResult> search(String query) {

        List<NutritionResult> results =
                new ArrayList<>();

        if (query == null || query.isBlank()) {
            return results;
        }

        List<NutritionResult> found =
                searchProvider(usdaProvider, query);

        if (!found.isEmpty()) {
            return found;
        }

        return searchProvider(
                openFoodFactsProvider,
                query
        );
    }


    // =========================================================
    // FIND BY BARCODE
    // =========================================================

    public NutritionResult findByBarcode(
            String barcode
    ) {

        if (barcode == null || barcode.isBlank()) {
            return null;
        }

        try {
            return openFoodFactsProvider
                    .findByBarcode(barcode);

        } catch (Exception e) {
            logger.warn(
                    "Open Food Facts barcode lookup failed ({})",
                    e.getClass().getSimpleName()
            );
            return null;
        }
    }


    // =========================================================
    // FIND EXACT SELECTED ITEM
    // =========================================================

    public NutritionResult findBySourceId(
            String source,
            String sourceId
    ) {

        if (
                source == null ||
                        sourceId == null ||
                        sourceId.isBlank()
        ) {
            return null;
        }

        NutritionProvider provider =
                providerFor(source.trim());

        if (provider == null) {
            return null;
        }

        try {
            return provider.findBySourceId(sourceId);

        } catch (Exception e) {
            logger.warn(
                    "{} source lookup failed ({})",
                    provider.getProviderName(),
                    e.getClass().getSimpleName()
            );
            return null;
        }
    }


    private List<NutritionResult> searchProvider(
            NutritionProvider provider,
            String query) {

        try {
            List<NutritionResult> found =
                    provider.search(query);

            return found == null
                    ? new ArrayList<>()
                    : found;

        } catch (Exception e) {
            logger.warn(
                    "{} search failed ({})",
                    provider.getProviderName(),
                    e.getClass().getSimpleName()
            );
            return new ArrayList<>();
        }
    }


    private NutritionProvider providerFor(
            String source) {

        if (usdaProvider
                .getProviderName()
                .equalsIgnoreCase(source) ||
                "USDA".equalsIgnoreCase(source)) {

            return usdaProvider;
        }

        if (openFoodFactsProvider
                .getProviderName()
                .equalsIgnoreCase(source) ||
                "OFF".equalsIgnoreCase(source)) {

            return openFoodFactsProvider;
        }

        return null;
    }
}
