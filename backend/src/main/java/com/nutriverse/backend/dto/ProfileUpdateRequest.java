package com.nutriverse.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProfileUpdateRequest {

    @Min(1)
    @Max(120)
    private Integer age;

    @Min(50)
    @Max(250)
    private Double height;

    @Min(10)
    @Max(500)
    private Double weight;

    @Pattern(regexp = "MALE|FEMALE|OTHER")
    private String gender;

    @Pattern(regexp = "VEGETARIAN|VEGAN|NON_VEGETARIAN")
    private String dietType;

    @Pattern(
            regexp = "SEDENTARY|LIGHTLY_ACTIVE|MODERATELY_ACTIVE|VERY_ACTIVE|EXTRA_ACTIVE"
    )
    private String activityLevel;

    @Pattern(
            regexp = "WEIGHT_LOSS|WEIGHT_GAIN|MAINTENANCE|HEALTHY_EATING|FITNESS"
    )
    private String goal;

    @Size(max = 200)
    private String foodPreferences;

    @Size(max = 300)
    private String foodDislikes;

    // Medical restriction
    @Pattern(regexp = "GLUTEN_FREE|NONE")
    private String dietaryRestriction;

    // Cultural / religious preference
    @Pattern(regexp = "JAIN|HALAL|KOSHER|NONE")
    private String religiousDiet;

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDietType() {
        return dietType;
    }

    public void setDietType(String dietType) {
        this.dietType = dietType;
    }

    public String getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(String activityLevel) {
        this.activityLevel = activityLevel;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getFoodPreferences() {
        return foodPreferences;
    }

    public void setFoodPreferences(String foodPreferences) {
        this.foodPreferences = foodPreferences;
    }

    public String getFoodDislikes() {
        return foodDislikes;
    }

    public void setFoodDislikes(String foodDislikes) {
        this.foodDislikes = foodDislikes;
    }

    public String getDietaryRestriction() {
        return dietaryRestriction;
    }

    public void setDietaryRestriction(String dietaryRestriction) {
        this.dietaryRestriction = dietaryRestriction;
    }

    public String getReligiousDiet() {
        return religiousDiet;
    }

    public void setReligiousDiet(String religiousDiet) {
        this.religiousDiet = religiousDiet;
    }
}