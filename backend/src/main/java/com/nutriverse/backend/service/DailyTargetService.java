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
            clearTargets(profile);
            return profileRepository.save(profile);
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

        if (!Double.isFinite(calorieTarget) || calorieTarget <= 0) {
            clearTargets(profile);
            return profileRepository.save(profile);
        }

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
                && profile.getAge() >= 18 && profile.getAge() <= 120
                && profile.getHeight() != null
                && Double.isFinite(profile.getHeight()) && profile.getHeight() >= 50 && profile.getHeight() <= 250
                && profile.getWeight() != null
                && Double.isFinite(profile.getWeight()) && profile.getWeight() >= 10 && profile.getWeight() <= 500
                && profile.getGender() != null
                && java.util.Set.of("SEDENTARY", "LIGHTLY_ACTIVE", "MODERATELY_ACTIVE", "VERY_ACTIVE", "EXTRA_ACTIVE")
                        .contains(profile.getActivityLevel() == null ? "" : profile.getActivityLevel());
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
