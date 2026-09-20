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
            DailyTargetService dailyTargetService
    ) {

        this.nutritionProfileRepository =
                nutritionProfileRepository;

        this.dailyTargetService =
                dailyTargetService;
    }


    public NutritionProfile getOrCreateProfile(
            String userId
    ) {

        NutritionProfile profile =
                nutritionProfileRepository
                        .findByUserId(userId)
                        .orElseGet(() -> {

                            NutritionProfile newProfile =
                                    new NutritionProfile(userId);

                            return nutritionProfileRepository
                                    .save(newProfile);
                        });


        NutritionProfile calculated =
                dailyTargetService
                        .calculateTargets(userId);


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


        if (request.getDietType() != null) {
            profile.setDietType(
                    request.getDietType()
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


        if (request.getFoodPreferences() != null) {

            String foodPreferences =
                    request
                            .getFoodPreferences()
                            .trim();

            profile.setFoodPreferences(
                    foodPreferences.isEmpty()
                            ? null
                            : foodPreferences
            );
        }


        /*
         * Only user-editable information is accepted.
         *
         * Daily calorie, protein and water targets are
         * calculated by the backend and are not accepted
         * from the frontend.
         */
        nutritionProfileRepository.save(profile);


        NutritionProfile calculatedProfile =
                dailyTargetService
                        .calculateTargets(userId);


        if (calculatedProfile != null) {
            return calculatedProfile;
        }


        return profile;
    }
}