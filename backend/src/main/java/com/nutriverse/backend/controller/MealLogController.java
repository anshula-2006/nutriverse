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

    @GetMapping("/today")
    public List<MealLog> getTodayMeals(
            @RequestAttribute("authenticatedUserId") String userId) {

        return mealLogService.getTodayMeals(userId);
    }

    @GetMapping
    public List<MealLog> getAllMeals(
            @RequestAttribute("authenticatedUserId") String userId) {

        return mealLogService.getAllMeals(userId);
    }
}
