package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.NutritionMealRequest;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.service.NutritionLookupService;
import com.nutriverse.backend.service.NutritionMealService;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {

    private final NutritionLookupService lookupService;
    private final NutritionMealService mealService;

    public NutritionController(
            NutritionLookupService lookupService,
            NutritionMealService mealService) {

        this.lookupService = lookupService;
        this.mealService = mealService;
    }

    @GetMapping("/search")
    public List<NutritionResult> search(
            @RequestParam String query) {

        return lookupService.search(query);
    }

    @GetMapping("/barcode/{barcode}")
    public NutritionResult barcode(
            @PathVariable String barcode) {

        NutritionResult result = lookupService.findByBarcode(barcode);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No nutrition record was found for this barcode");
        }
        return result;
    }

    @GetMapping("/food")
    public NutritionResult food(@RequestParam String source, @RequestParam String sourceId) {
        NutritionResult result = lookupService.findBySourceId(source, sourceId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected food could not be retrieved from its source");
        }
        return result;
    }

    @PostMapping("/log-meal")
    public MealLog logMeal(
            @RequestAttribute("authenticatedUserId") String userId,
            @Valid @RequestBody NutritionMealRequest request) {

        return mealService.logMeal(
                userId,
                request
        );
    }
}
