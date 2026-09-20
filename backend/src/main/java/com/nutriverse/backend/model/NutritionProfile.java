package com.nutriverse.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "nutrition_profiles")
public class NutritionProfile {

    @Id
    private String id;

    private String userId;

    // User profile
    private Integer age;
    private Double height;
    private Double weight;
    private String gender;

    // Nutrition preferences
    private String dietType;
    private String activityLevel;
    private String goal;
    private String foodPreferences;

    // Backend-calculated daily targets
    private Integer dailyCalorieTarget;
    private Integer dailyProteinTarget;
    private Double dailyWaterTarget;


    public NutritionProfile() {
    }


    public NutritionProfile(String userId) {
        this.userId = userId;
    }


    public String getId() {
        return id;
    }


    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }


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


    public Integer getDailyCalorieTarget() {
        return dailyCalorieTarget;
    }

    public void setDailyCalorieTarget(Integer dailyCalorieTarget) {
        this.dailyCalorieTarget = dailyCalorieTarget;
    }


    public Integer getDailyProteinTarget() {
        return dailyProteinTarget;
    }

    public void setDailyProteinTarget(Integer dailyProteinTarget) {
        this.dailyProteinTarget = dailyProteinTarget;
    }


    public Double getDailyWaterTarget() {
        return dailyWaterTarget;
    }

    public void setDailyWaterTarget(Double dailyWaterTarget) {
        this.dailyWaterTarget = dailyWaterTarget;
    }
}