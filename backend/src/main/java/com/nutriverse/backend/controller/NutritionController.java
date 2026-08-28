package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.NutritionMealRequest;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.service.NutritionLookupService;
import com.nutriverse.backend.service.NutritionMealService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {

    private final NutritionLookupService lookupService;
    private final NutritionMealService mealService;

    public NutritionController(
            NutritionLookupService lookupService,
            NutritionMealService mealService
    ) {
        this.lookupService = lookupService;
        this.mealService = mealService;
    }

    // Search by food/product name
    @GetMapping("/search")
    public List<NutritionResult> search(
            @RequestParam String query
    ) {
        return lookupService.search(query);
    }

    // Search exact packaged product by barcode
    @GetMapping("/barcode/{barcode}")
    public NutritionResult barcode(
            @PathVariable String barcode
    ) {
        return lookupService.findByBarcode(barcode);
    }

    // Log selected food with actual quantity
    @PostMapping("/log-meal")
    public MealLog logMeal(
            @RequestBody NutritionMealRequest request
    ) {
        return mealService.logMeal(request);
    }
}