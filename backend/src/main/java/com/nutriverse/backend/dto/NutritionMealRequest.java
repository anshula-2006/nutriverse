package com.nutriverse.backend.dto;

public class NutritionMealRequest {

    private String userId;
    private String mealType;

    private String source;
    private String sourceId;

    private Double quantityGrams;

    public NutritionMealRequest() {
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Double getQuantityGrams() {
        return quantityGrams;
    }

    public void setQuantityGrams(Double quantityGrams) {
        this.quantityGrams = quantityGrams;
    }
}