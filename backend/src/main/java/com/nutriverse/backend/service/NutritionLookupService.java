package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NutritionLookupService {

    private final List<NutritionProvider> providers;

    public NutritionLookupService(
            List<NutritionProvider> providers
    ) {
        this.providers = providers;
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

        for (NutritionProvider provider : providers) {

            try {

                List<NutritionResult> found =
                        provider.search(query);

                if (found != null) {
                    results.addAll(found);
                }

            } catch (Exception e) {

                System.out.println(
                        provider.getProviderName()
                                + " search failed: "
                                + e.getMessage()
                );
            }
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

        for (NutritionProvider provider : providers) {

            try {

                NutritionResult result =
                        provider.findByBarcode(barcode);

                if (result != null) {
                    return result;
                }

            } catch (Exception e) {

                System.out.println(
                        provider.getProviderName()
                                + " barcode lookup failed: "
                                + e.getMessage()
                );
            }
        }

        return null;
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

        for (NutritionProvider provider : providers) {

            if (!provider
                    .getProviderName()
                    .equalsIgnoreCase(source)) {
                continue;
            }

            try {

                return provider
                        .findBySourceId(sourceId);

            } catch (Exception e) {

                System.out.println(
                        provider.getProviderName()
                                + " source lookup failed: "
                                + e.getMessage()
                );
            }
        }

        return null;
    }
}