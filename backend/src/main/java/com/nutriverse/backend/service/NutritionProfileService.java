package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.ProfileUpdateRequest;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class NutritionProfileService {

    private final NutritionProfileRepository repository;
    private final DailyTargetService dailyTargetService;

    public NutritionProfileService(
            NutritionProfileRepository repository,
            DailyTargetService dailyTargetService
    ) {
        this.repository = repository;
        this.dailyTargetService = dailyTargetService;
    }

    public NutritionProfile getOrCreateProfile(String userId) {

        NutritionProfile profile = repository
                .findByUserId(userId)
                .orElseGet(() ->
                        repository.save(
                                new NutritionProfile(userId)
                        )
                );

        NutritionProfile calculated =
                dailyTargetService.calculateTargets(userId);

        return calculated == null
                ? profile
                : calculated;
    }

    public NutritionProfile updateProfile(
            String userId,
            ProfileUpdateRequest request
    ) {

        NutritionProfile profile =
                getOrCreateProfile(userId);

        if (request.getAge() != null)
            profile.setAge(request.getAge());

        if (request.getHeight() != null)
            profile.setHeight(request.getHeight());

        if (request.getWeight() != null)
            profile.setWeight(request.getWeight());

        if (request.getGender() != null)
            profile.setGender(request.getGender());

        if (request.getDietType() != null)
            profile.setDietType(request.getDietType());

        if (request.getActivityLevel() != null)
            profile.setActivityLevel(
                    request.getActivityLevel()
            );

        if (request.getGoal() != null)
            profile.setGoal(request.getGoal());

        if (request.getFoodPreferences() != null)
            profile.setFoodPreferences(
                    clean(request.getFoodPreferences())
            );

        if (request.getFoodDislikes() != null)
            profile.setFoodDislikes(
                    clean(request.getFoodDislikes())
            );

        if (request.getDietaryRestriction() != null) {
            profile.setDietaryRestriction(
                    nullablePreference(
                            request.getDietaryRestriction()
                    )
            );
        }

        if (request.getReligiousDiet() != null) {
            profile.setReligiousDiet(
                    nullablePreference(
                            request.getReligiousDiet()
                    )
            );
        }

        repository.save(profile);

        NutritionProfile calculated =
                dailyTargetService.calculateTargets(userId);

        return calculated == null
                ? profile
                : calculated;
    }

    private String nullablePreference(String value) {
        return "NONE".equals(value)
                ? null
                : value;
    }

    private String clean(String value) {

        String cleaned =
                value == null
                        ? ""
                        : value.trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }
}