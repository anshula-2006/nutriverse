package com.nutriverse.backend.controller;

import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.service.MealLogService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meals")
public class MealLogController {

    private final MealLogService mealLogService;

    public MealLogController(MealLogService mealLogService) {
        this.mealLogService = mealLogService;
    }

    @GetMapping("/today/{userId}")
    public List<MealLog> getTodayMeals(
            @PathVariable String userId) {

        return mealLogService.getTodayMeals(userId);
    }

    @GetMapping("/{userId}")
    public List<MealLog> getAllMeals(
            @PathVariable String userId) {

        return mealLogService.getAllMeals(userId);
    }
}
