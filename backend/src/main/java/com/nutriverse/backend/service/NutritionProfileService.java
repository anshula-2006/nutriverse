package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.ProfileUpdateRequest;
import com.nutriverse.backend.dto.BmiResponse;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class NutritionProfileService {

    private final NutritionProfileRepository nutritionProfileRepository;

    public NutritionProfileService(
            NutritionProfileRepository nutritionProfileRepository
    ) {
        this.nutritionProfileRepository = nutritionProfileRepository;
    }

    public NutritionProfile getOrCreateProfile(String userId) {

        return nutritionProfileRepository
                .findByUserId(userId)
                .orElseGet(() -> {

                    NutritionProfile profile =
                            new NutritionProfile(userId);

                    return nutritionProfileRepository.save(profile);
                });
    }

    public NutritionProfile updateProfile(
            String userId,
            ProfileUpdateRequest request
    ) {

        NutritionProfile profile = getOrCreateProfile(userId);

        if (request.getAge() != null) {
            profile.setAge(request.getAge());
        }

        if (request.getHeight() != null) {
            profile.setHeight(request.getHeight());
        }

        if (request.getWeight() != null) {
            profile.setWeight(request.getWeight());
        }

        if (request.getGender() != null) {
            profile.setGender(request.getGender());
        }

        if (request.getActivityLevel() != null) {
            profile.setActivityLevel(request.getActivityLevel());
        }

        if (request.getGoal() != null) {
            profile.setGoal(request.getGoal());
        }

        if (request.getDailyCalorieTarget() != null) {
            profile.setDailyCalorieTarget(
                    request.getDailyCalorieTarget()
            );
        }

        if (request.getDailyProteinTarget() != null) {
            profile.setDailyProteinTarget(
                    request.getDailyProteinTarget()
            );
        }

        if (request.getDailyWaterTarget() != null) {
            profile.setDailyWaterTarget(
                    request.getDailyWaterTarget()
            );
        }

        return nutritionProfileRepository.save(profile);
    }

    public BmiResponse getBmiResponse(String userId) {
        NutritionProfile profile = getOrCreateProfile(userId);

        if(profile.getHeight() == null || profile.getWeight() == null) {
            return new BmiResponse(0, "Not Available");
        }

        double heightInMeters = profile.getHeight() / 100.0;

        double bmi = profile.getWeight() / (heightInMeters * heightInMeters);

        bmi = Math.round(bmi * 10.0) / 10.0;

        String category;

        if (bmi < 18.5) {
            category = "Underweight";
        }else if(bmi < 25){
            category = "Normal";
        }else if(bmi < 30){
            category = "Overweight";
        }else{
            category = "Obese";
        }
        return new BmiResponse(bmi, category);
    }
}