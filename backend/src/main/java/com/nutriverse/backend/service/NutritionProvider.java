package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;

import java.util.List;

public interface NutritionProvider {

    List<NutritionResult> search(String query);

    default NutritionResult findByBarcode(String barcode) {
        return null;
    }

    default NutritionResult findBySourceId(String sourceId) {
        return null;
    }

    String getProviderName();
}