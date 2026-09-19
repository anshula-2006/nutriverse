package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionMealRequest;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.repository.MealLogRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NutritionMealTests {
    private final NutritionLookupService lookup = mock(NutritionLookupService.class);
    private final MealLogRepository meals = mock(MealLogRepository.class);
    private final NutritionMealService service = new NutritionMealService(lookup, meals);

    @Test
    void logsExactRefetchedFoodForAuthenticatedOwnerAndKeepsUnknownValuesNull() {
        NutritionResult food = food();
        when(lookup.findBySourceId("USDA FoodData Central", "173944")).thenReturn(food);
        when(meals.save(any(MealLog.class))).thenAnswer(call -> call.getArgument(0));
        MealLog meal = service.logMeal("authenticated-owner", request(150.0));
        verify(lookup).findBySourceId("USDA FoodData Central", "173944");
        assertEquals("authenticated-owner", meal.getUserId());
        assertEquals("Bananas, raw", meal.getFoodName());
        assertEquals(133.5, meal.getCalories());
        assertEquals(1.64, meal.getProtein());
        assertNull(meal.getFiber());
        assertEquals("USDA FoodData Central", meal.getSource());
        assertEquals("173944", meal.getSourceId());
        assertTrue(meal.isVerified());
        assertFalse(meal.isEstimated());
    }

    @Test
    void rejectsNonfiniteInvalidOrExcessiveQuantitiesBeforeLookup() {
        for (double amount : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0, 10001}) {
            assertThrows(IllegalArgumentException.class, () -> service.logMeal("owner", request(amount)));
        }
        NutritionMealRequest request = request(100.0);
        request.setMealType("unknown");
        assertThrows(IllegalArgumentException.class, () -> service.logMeal("owner", request));
        verifyNoInteractions(lookup, meals);
    }

    @Test
    void rejectsWrongIdentityMissingBasisVolumeOrForgedVerification() {
        NutritionResult food = food();
        when(lookup.findBySourceId("USDA FoodData Central", "173944")).thenReturn(food);
        food.setSourceId("999");
        assertThrows(IllegalArgumentException.class, () -> service.logMeal("owner", request(100.0)));
        food.setSourceId("173944");
        food.setServingSize(null);
        assertThrows(IllegalArgumentException.class, () -> service.logMeal("owner", request(100.0)));
        food.setServingSize(100.0);
        food.setServingUnit("ml");
        assertThrows(IllegalArgumentException.class, () -> service.logMeal("owner", request(100.0)));
        food.setServingUnit("g");
        food.setSource("Open Food Facts");
        food.setSourceType("PRODUCT_DATABASE");
        assertThrows(IllegalArgumentException.class, () -> service.logMeal("owner", request(100.0)));
        verifyNoInteractions(meals);
    }

    private NutritionMealRequest request(Double amount) {
        NutritionMealRequest request = new NutritionMealRequest();
        request.setSource("USDA FoodData Central");
        request.setSourceId("173944");
        request.setMealType("SNACK");
        request.setQuantityGrams(amount);
        return request;
    }

    private NutritionResult food() {
        NutritionResult food = new NutritionResult();
        food.setFoodName("Bananas, raw");
        food.setSource("USDA FoodData Central");
        food.setSourceType("AUTHORITATIVE_DATABASE");
        food.setSourceId("173944");
        food.setServingSize(100.0);
        food.setServingUnit("g");
        food.setCalories(89.0);
        food.setProtein(1.09);
        food.setVerified(true);
        return food;
    }
}
