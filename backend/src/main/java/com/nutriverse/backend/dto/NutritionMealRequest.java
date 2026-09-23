package com.nutriverse.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class NutritionMealRequest {

    @NotBlank
    @Pattern(regexp = "BREAKFAST|LUNCH|DINNER|SNACK")
    private String mealType;
    @NotBlank
    @Size(max = 80)
    private String source;
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._-]{1,32}")
    private String sourceId;
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @DecimalMax("10000")
    private Double quantityGrams;

    public NutritionMealRequest() {
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
