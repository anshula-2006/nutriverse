package com.nutriverse.backend.service;

import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class DailyTargetService {

    private final NutritionProfileRepository profileRepository;

    public DailyTargetService(
            NutritionProfileRepository profileRepository
    ) {
        this.profileRepository = profileRepository;
    }


    // =========================================================
    // CALCULATE AND SAVE DAILY TARGETS
    // =========================================================

    public NutritionProfile calculateTargets(String userId) {

        NutritionProfile profile =
                profileRepository
                        .findByUserId(userId)
                        .orElse(null);

        if (profile == null) {
            return null;
        }

        // We need these values before calculating targets
        if (
                profile.getAge() == null ||
                        profile.getHeight() == null ||
                        profile.getWeight() == null ||
                        profile.getGender() == null ||
                        profile.getActivityLevel() == null
        ) {
            return profile;
        }


        // -----------------------------------------------------
        // 1. CALCULATE BMR
        // Mifflin-St Jeor Equation
        // -----------------------------------------------------

        double bmr;

        if (
                profile.getGender()
                        .equalsIgnoreCase("MALE")
        ) {

            bmr =
                    (10 * profile.getWeight()) +
                            (6.25 * profile.getHeight()) -
                            (5 * profile.getAge()) +
                            5;

        } else {

            bmr =
                    (10 * profile.getWeight()) +
                            (6.25 * profile.getHeight()) -
                            (5 * profile.getAge()) -
                            161;
        }


        // -----------------------------------------------------
        // 2. ACTIVITY MULTIPLIER
        // -----------------------------------------------------

        double activityMultiplier =
                getActivityMultiplier(
                        profile.getActivityLevel()
                );


        double maintenanceCalories =
                bmr * activityMultiplier;


        // -----------------------------------------------------
        // 3. MODIFY BASED ON USER GOAL
        // -----------------------------------------------------

        double calorieTarget =
                maintenanceCalories;

        if (profile.getGoal() != null) {

            switch (
                    profile.getGoal()
                            .toUpperCase()
            ) {

                case "WEIGHT_LOSS":
                    calorieTarget -= 500;
                    break;

                case "WEIGHT_GAIN":
                    calorieTarget += 300;
                    break;

                case "MAINTENANCE":
                case "HEALTHIER":
                case "FITNESS":
                default:
                    break;
            }
        }


        // -----------------------------------------------------
        // 4. PROTEIN TARGET
        // -----------------------------------------------------

        double proteinPerKg = 1.2;

        if (profile.getGoal() != null) {

            switch (
                    profile.getGoal()
                            .toUpperCase()
            ) {

                case "WEIGHT_LOSS":
                    proteinPerKg = 1.2;
                    break;

                case "WEIGHT_GAIN":
                case "FITNESS":
                    proteinPerKg = 1.5;
                    break;

                default:
                    proteinPerKg = 1.0;
                    break;
            }
        }


        int proteinTarget =
                (int) Math.round(
                        profile.getWeight()
                                * proteinPerKg
                );


        // -----------------------------------------------------
        // 5. WATER TARGET
        // 35 ml per kg body weight
        // -----------------------------------------------------

        double waterTarget =
                profile.getWeight()
                        * 0.035;


        waterTarget =
                Math.round(
                        waterTarget * 10.0
                ) / 10.0;


        // -----------------------------------------------------
        // 6. SAVE TARGETS TO PROFILE
        // -----------------------------------------------------

        profile.setDailyCalorieTarget(
                (int) Math.round(
                        calorieTarget
                )
        );

        profile.setDailyProteinTarget(
                proteinTarget
        );

        profile.setDailyWaterTarget(
                waterTarget
        );


        return profileRepository.save(profile);
    }


    // =========================================================
    // ACTIVITY LEVEL
    // =========================================================

    private double getActivityMultiplier(
            String activityLevel
    ) {

        if (activityLevel == null) {
            return 1.2;
        }

        return switch (
                activityLevel.toUpperCase()
                ) {

            case "LIGHTLY_ACTIVE" ->
                    1.375;

            case "MODERATELY_ACTIVE" ->
                    1.55;

            case "VERY_ACTIVE" ->
                    1.725;

            case "SEDENTARY" ->
                    1.2;

            default ->
                    1.2;
        };
    }
}