package com.nutriverse.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public class ProfileUpdateRequest {

    @Min(value = 1, message = "Age must be at least 1")
    @Max(value = 120, message = "Age must not exceed 120")
    private Integer age;

    @Min(value = 50, message = "Height must be at least 50 cm")
    @Max(value = 250, message = "Height must not exceed 250 cm")
    private Double height;

    @Min(value = 10, message = "Weight must be at least 10 kg")
    @Max(value = 500, message = "Weight must not exceed 500 kg")
    private Double weight;

    @Pattern(
            regexp = "MALE|FEMALE|OTHER",
            message = "Gender must be MALE, FEMALE or OTHER"
    )
    private String gender;

    @Pattern(
            regexp = "VEGETARIAN|VEGAN|NON_VEGETARIAN",
            message = "Invalid diet preference"
    )
    private String dietType;

    @Pattern(
            regexp = "SEDENTARY|LIGHTLY_ACTIVE|MODERATELY_ACTIVE|VERY_ACTIVE|EXTRA_ACTIVE",
            message = "Invalid activity level"
    )
    private String activityLevel;

    @Pattern(
            regexp = "WEIGHT_LOSS|WEIGHT_GAIN|MAINTENANCE|HEALTHY_EATING|FITNESS",
            message = "Invalid nutrition goal"
    )
    private String goal;


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
}