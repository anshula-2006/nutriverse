package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.ProfileUpdateRequest;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;

import org.springframework.stereotype.Service;

@Service
public class NutritionProfileService {

    private final NutritionProfileRepository nutritionProfileRepository;
    private final DailyTargetService dailyTargetService;

    public NutritionProfileService(
            NutritionProfileRepository nutritionProfileRepository,
            DailyTargetService dailyTargetService) {

        this.nutritionProfileRepository =
                nutritionProfileRepository;

        this.dailyTargetService =
                dailyTargetService;
    }


    public NutritionProfile getOrCreateProfile(
            String userId) {

        return nutritionProfileRepository
                .findByUserId(userId)
                .orElseGet(() -> {

                    NutritionProfile profile =
                            new NutritionProfile(userId);

                    return nutritionProfileRepository
                            .save(profile);
                });
    }


    public NutritionProfile updateProfile(
            String userId,
            ProfileUpdateRequest request) {

        NutritionProfile profile =
                getOrCreateProfile(userId);


        if (request.getAge() != null) {

            profile.setAge(
                    request.getAge()
            );
        }


        if (request.getHeight() != null) {

            profile.setHeight(
                    request.getHeight()
            );
        }


        if (request.getWeight() != null) {

            profile.setWeight(
                    request.getWeight()
            );
        }


        if (request.getGender() != null) {

            profile.setGender(
                    request.getGender()
            );
        }


        if (request.getActivityLevel() != null) {

            profile.setActivityLevel(
                    request.getActivityLevel()
            );
        }


        if (request.getGoal() != null) {

            profile.setGoal(
                    request.getGoal()
            );
        }


        // Save user-controlled profile fields first
        nutritionProfileRepository.save(profile);


        // Recalculate system-controlled targets
        NutritionProfile updatedProfile =
                dailyTargetService
                        .calculateTargets(userId);


        if (updatedProfile != null) {

            return updatedProfile;
        }


        return profile;
    }
}