package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

        if (query.length() > 200) {
            throw new IllegalArgumentException("Search query is too long");
        }
        boolean providerFailed = false;
        for (NutritionProvider provider : List.of(usdaProvider, openFoodFactsProvider)) {
            try {
                List<NutritionResult> found = provider.search(query.trim());
                if (found == null) {
                    providerFailed = true;
                } else if (!found.isEmpty()) {
                    return found;
                }
            } catch (RuntimeException error) {
                logger.warn("{} search unavailable: type={}", provider.getProviderName(), error.getClass().getSimpleName());
                providerFailed = true;
            }
        }
        if (providerFailed) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Nutrition search is temporarily unavailable. Please try again shortly.");
        }
        return results;
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

        return openFoodFactsProvider.findByBarcode(barcode);
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

        NutritionResult result = provider.findBySourceId(sourceId.trim());
        if (result != null && (!sourceId.trim().equals(result.getSourceId())
                || !provider.getProviderName().equals(result.getSource()))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Nutrition provider returned a different food. Please search again.");
        }
        return result;
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
