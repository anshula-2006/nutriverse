package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.ProfileUpdateRequest;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.service.NutritionProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class NutritionProfileController {

    private final NutritionProfileService nutritionProfileService;

    public NutritionProfileController(
            NutritionProfileService nutritionProfileService
    ) {
        this.nutritionProfileService = nutritionProfileService;
    }

    @GetMapping("/{userId}")
    public NutritionProfile getProfile(
            @PathVariable String userId
    ) {
        return nutritionProfileService.getOrCreateProfile(userId);
    }

    @PatchMapping("/{userId}")
    public NutritionProfile updateProfile(
            @PathVariable String userId,
            @RequestBody ProfileUpdateRequest request
    ) {
        return nutritionProfileService.updateProfile(
                userId,
                request
        );
    }
}