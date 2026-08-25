package com.nutriverse.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "meal_logs")
public class MealLog {

    @Id
    private String id;

    private String userId;

    private String mealType;
    private String foodName;

    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;

    private LocalDateTime loggedAt;


    public MealLog() {
    }


    public MealLog(
            String userId,
            String mealType,
            String foodName,
            Double calories,
            Double protein,
            Double carbs,
            Double fat
    ) {
        this.userId = userId;
        this.mealType = mealType;
        this.foodName = foodName;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.loggedAt = LocalDateTime.now();
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


    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }


    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }


    public Double getCalories() {
        return calories;
    }

    public void setCalories(Double calories) {
        this.calories = calories;
    }


    public Double getProtein() {
        return protein;
    }

    public void setProtein(Double protein) {
        this.protein = protein;
    }


    public Double getCarbs() {
        return carbs;
    }

    public void setCarbs(Double carbs) {
        this.carbs = carbs;
    }


    public Double getFat() {
        return fat;
    }

    public void setFat(Double fat) {
        this.fat = fat;
    }


    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(LocalDateTime loggedAt) {
        this.loggedAt = loggedAt;
    }
}