package com.nutriverse.backend.dto;

import com.nutriverse.backend.model.MealLog;

import java.util.List;
import java.util.Map;

public class DashboardResponse {

    // User details
    private String name;
    private String goal;
    private String dietType;

    // Calories
    private double caloriesConsumed;
    private Integer calorieTarget;

    // Protein
    private double proteinConsumed;
    private Integer proteinTarget;

    // Water
    private double waterConsumed;
    private Double waterTarget;

    // BMI
    private double bmi;
    private String bmiCategory;

    // Macros
    private double carbsConsumed;
    private double fatConsumed;

    // Today's meals
    private List<MealLog> todayMeals;

    // Weekly calorie graph
    private Map<String, Double> weeklyCalories;


    public DashboardResponse() {
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }


    public String getDietType() {
        return dietType;
    }

    public void setDietType(String dietType) {
        this.dietType = dietType;
    }


    public double getCaloriesConsumed() {
        return caloriesConsumed;
    }

    public void setCaloriesConsumed(double caloriesConsumed) {
        this.caloriesConsumed = caloriesConsumed;
    }


    public Integer getCalorieTarget() {
        return calorieTarget;
    }

    public void setCalorieTarget(Integer calorieTarget) {
        this.calorieTarget = calorieTarget;
    }


    public double getProteinConsumed() {
        return proteinConsumed;
    }

    public void setProteinConsumed(double proteinConsumed) {
        this.proteinConsumed = proteinConsumed;
    }


    public Integer getProteinTarget() {
        return proteinTarget;
    }

    public void setProteinTarget(Integer proteinTarget) {
        this.proteinTarget = proteinTarget;
    }


    public double getWaterConsumed() {
        return waterConsumed;
    }

    public void setWaterConsumed(double waterConsumed) {
        this.waterConsumed = waterConsumed;
    }


    public Double getWaterTarget() {
        return waterTarget;
    }

    public void setWaterTarget(Double waterTarget) {
        this.waterTarget = waterTarget;
    }


    public double getBmi() {
        return bmi;
    }

    public void setBmi(double bmi) {
        this.bmi = bmi;
    }


    public String getBmiCategory() {
        return bmiCategory;
    }

    public void setBmiCategory(String bmiCategory) {
        this.bmiCategory = bmiCategory;
    }


    public double getCarbsConsumed() {
        return carbsConsumed;
    }

    public void setCarbsConsumed(double carbsConsumed) {
        this.carbsConsumed = carbsConsumed;
    }


    public double getFatConsumed() {
        return fatConsumed;
    }

    public void setFatConsumed(double fatConsumed) {
        this.fatConsumed = fatConsumed;
    }


    public List<MealLog> getTodayMeals() {
        return todayMeals;
    }

    public void setTodayMeals(List<MealLog> todayMeals) {
        this.todayMeals = todayMeals;
    }


    public Map<String, Double> getWeeklyCalories() {
        return weeklyCalories;
    }

    public void setWeeklyCalories(
            Map<String, Double> weeklyCalories
    ) {
        this.weeklyCalories = weeklyCalories;
    }
}