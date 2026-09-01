package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionMealRequest;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.repository.MealLogRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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

        if (request.getQuantityGrams() == null
                || request.getQuantityGrams() <= 0) {

            throw new IllegalArgumentException(
                    "Quantity must be greater than 0"
            );
        }

        NutritionResult food =
                nutritionLookupService.findBySourceId(
                        request.getSource(),
                        request.getSourceId()
                );

        if (food == null) {
            throw new IllegalArgumentException(
                    "Selected food could not be found"
            );
        }

        double baseSize =
                food.getServingSize() != null
                        ? food.getServingSize()
                        : 100.0;

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

        if (value == null) {
            return null;
        }

        return Math.round(
                value * factor * 100.0
        ) / 100.0;
    }
}