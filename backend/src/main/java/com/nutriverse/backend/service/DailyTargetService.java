package com.nutriverse.backend.service;

import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;

import org.springframework.stereotype.Service;

@Service
public class DailyTargetService {

    private final NutritionProfileRepository profileRepository;

    public DailyTargetService(
            NutritionProfileRepository profileRepository) {

        this.profileRepository = profileRepository;
    }

    public NutritionProfile calculateTargets(
            String userId) {

        NutritionProfile profile =
                profileRepository
                        .findByUserId(userId)
                        .orElse(null);

        if (profile == null) {
            return null;
        }

        if (!hasRequiredData(profile)) {
            return profile;
        }

        Double bmr =
                calculateBmr(profile);

        if (bmr == null) {
            clearTargets(profile);
            return profileRepository.save(profile);
        }

        double maintenanceCalories =
                bmr * getActivityMultiplier(
                        profile.getActivityLevel()
                );

        double calorieTarget =
                applyGoalAdjustment(
                        maintenanceCalories,
                        profile.getGoal()
                );

        double proteinPerKg =
                getProteinPerKg(
                        profile.getGoal()
                );

        int proteinTarget =
                (int) Math.round(
                        profile.getWeight()
                                * proteinPerKg
                );

        double waterTarget =
                Math.round(
                        profile.getWeight()
                                * 0.035
                                * 10.0
                ) / 10.0;

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


    private boolean hasRequiredData(
            NutritionProfile profile) {

        return profile.getAge() != null
                && profile.getHeight() != null
                && profile.getWeight() != null
                && profile.getGender() != null
                && profile.getActivityLevel() != null;
    }


    private Double calculateBmr(
            NutritionProfile profile) {

        double base =
                (10 * profile.getWeight())
                        + (6.25 * profile.getHeight())
                        - (5 * profile.getAge());

        return switch (
                profile.getGender()
                        .toUpperCase()
                ) {

            case "MALE" ->
                    base + 5;

            case "FEMALE" ->
                    base - 161;

            default ->
                    null;
        };
    }


    private double getActivityMultiplier(
            String activityLevel) {

        return switch (
                activityLevel.toUpperCase()
                ) {

            case "LIGHTLY_ACTIVE" ->
                    1.375;

            case "MODERATELY_ACTIVE" ->
                    1.55;

            case "VERY_ACTIVE" ->
                    1.725;

            case "EXTRA_ACTIVE" ->
                    1.9;

            case "SEDENTARY" ->
                    1.2;

            default ->
                    1.2;
        };
    }


    private double applyGoalAdjustment(
            double maintenanceCalories,
            String goal) {

        if (goal == null) {
            return maintenanceCalories;
        }

        return switch (
                goal.toUpperCase()
                ) {

            case "WEIGHT_LOSS" ->
                    maintenanceCalories - 500;

            case "WEIGHT_GAIN" ->
                    maintenanceCalories + 300;

            case "MAINTENANCE",
                 "HEALTHY_EATING",
                 "FITNESS" ->
                    maintenanceCalories;

            default ->
                    maintenanceCalories;
        };
    }


    private double getProteinPerKg(
            String goal) {

        if (goal == null) {
            return 1.0;
        }

        return switch (
                goal.toUpperCase()
                ) {

            case "WEIGHT_LOSS" ->
                    1.2;

            case "WEIGHT_GAIN",
                 "FITNESS" ->
                    1.5;

            default ->
                    1.0;
        };
    }


    private void clearTargets(
            NutritionProfile profile) {

        profile.setDailyCalorieTarget(null);
        profile.setDailyProteinTarget(null);
        profile.setDailyWaterTarget(null);
    }
}