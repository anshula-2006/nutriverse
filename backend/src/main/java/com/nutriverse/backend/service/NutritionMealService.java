package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionMealRequest;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.repository.MealLogRepository;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class NutritionMealService {

    private final NutritionLookupService nutritionLookupService;
    private final MealLogRepository mealLogRepository;

    public NutritionMealService(
            NutritionLookupService nutritionLookupService,
            MealLogRepository mealLogRepository) {

        this.nutritionLookupService = nutritionLookupService;
        this.mealLogRepository = mealLogRepository;
    }

    public MealLog logMeal(
            String userId,
            NutritionMealRequest request) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated user is required"
            );
        }

        if (request == null || request.getQuantityGrams() == null
                || !Double.isFinite(request.getQuantityGrams())
                || request.getQuantityGrams() <= 0 || request.getQuantityGrams() > 10000) {

            throw new IllegalArgumentException(
                    "Quantity must be greater than 0 and at most 10000 grams"
            );
        }
        if (request.getMealType() == null
                || !Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(request.getMealType())) {
            throw new IllegalArgumentException("Select a valid meal type");
        }

        NutritionResult food =
                nutritionLookupService.findBySourceId(
                        request.getSource(),
                        request.getSourceId()
                );

        if (food == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Selected food could not be retrieved from its source. Please search again.");
        }

        if (food.getServingSize() == null || !Double.isFinite(food.getServingSize())
                || food.getServingSize() <= 0 || !"g".equals(food.getServingUnit())
                || food.getSourceId() == null || !food.getSourceId().equalsIgnoreCase(request.getSourceId())
                || food.getSource() == null
                || !food.getSource().equalsIgnoreCase(request.getSource())
                || food.getSourceType() == null) {
            throw new IllegalArgumentException("Selected food has no matching source or gram-based serving");
        }
        boolean supportedSourceType =
                "AUTHORITATIVE_DATABASE".equals(food.getSourceType())
                        || "PRODUCT_DATABASE".equals(food.getSourceType());

        if (!supportedSourceType
                || !food.isVerified()
                || food.isEstimated()) {

            throw new IllegalArgumentException(
                    "Selected food provenance could not be confirmed"
            );
        }

        double baseSize = food.getServingSize();

        double factor =
                request.getQuantityGrams() / baseSize;

        MealLog meal = new MealLog();

        meal.setUserId(userId);
        meal.setMealType(request.getMealType());

        meal.setFoodName(food.getFoodName());

        meal.setQuantity(request.getQuantityGrams());
        meal.setServingUnit("g");

        meal.setCalories(scale(food.getCalories(), factor));
        meal.setProtein(scale(food.getProtein(), factor));
        meal.setCarbs(scale(food.getCarbs(), factor));
        meal.setFat(scale(food.getFat(), factor));
        meal.setFiber(scale(food.getFiber(), factor));

        meal.setIron(scale(food.getIron(), factor));
        meal.setCalcium(scale(food.getCalcium(), factor));
        meal.setSodium(scale(food.getSodium(), factor));
        meal.setPotassium(scale(food.getPotassium(), factor));

        meal.setSourceType(food.getSourceType());
        meal.setSource(food.getSource());
        meal.setSourceId(food.getSourceId());

        meal.setVerified(food.isVerified());
        meal.setEstimated(food.isEstimated());
        meal.setConfidence(food.getConfidence());

        meal.setLoggedAt(LocalDateTime.now());

        return mealLogRepository.save(meal);
    }

    private Double scale(
            Double value,
            double factor) {

        if (value == null || !Double.isFinite(value) || value < 0) {
            return null;
        }
        double scaled = value * factor;
        if (!Double.isFinite(scaled) || scaled > Long.MAX_VALUE / 100.0) {
            throw new IllegalArgumentException("Nutrient value is outside the supported range");
        }
        return Math.round(scaled * 100.0) / 100.0;
    }
}
