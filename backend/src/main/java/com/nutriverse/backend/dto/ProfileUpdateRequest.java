package com.nutriverse.backend.dto;

public class ProfileUpdateRequest {

    private Integer age;
    private Double height;
    private Double weight;
    private String gender;

    private String activityLevel;
    private String goal;

    private Integer dailyCalorieTarget;
    private Integer dailyProteinTarget;
    private Double dailyWaterTarget;


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