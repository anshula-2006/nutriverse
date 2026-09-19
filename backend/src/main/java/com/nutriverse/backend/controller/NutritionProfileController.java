package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.ProfileUpdateRequest;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.service.NutritionProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class NutritionProfileController {
    private final NutritionProfileService service;
    public NutritionProfileController(NutritionProfileService service) { this.service = service; }

    @GetMapping
    public NutritionProfile getProfile(@RequestAttribute("authenticatedUserId") String userId) {
        return service.getOrCreateProfile(userId);
    }

    @PatchMapping
    public NutritionProfile updateProfile(@RequestAttribute("authenticatedUserId") String userId,
                                          @Valid @RequestBody ProfileUpdateRequest request) {
        return service.updateProfile(userId, request);
    }
}
