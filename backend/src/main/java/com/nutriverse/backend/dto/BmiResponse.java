package com.nutriverse.backend.dto;

public class BmiResponse {

    private double bmi;
    private String category;

    public BmiResponse(double bmi, String category) {
        this.bmi = bmi;
        this.category = category;
    }

    public double getBmi() {
        return bmi;
    }
    public String getCategory() {
        return category;
    }
}
