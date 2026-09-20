package com.nutriverse.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RecommendationRequest {

    @NotBlank(
            message = "Recommendation request is required"
    )
    @Size(
            max = 300,
            message = "Recommendation request must not exceed 300 characters"
    )
    private String request;


    public RecommendationRequest() {
    }


    public String getRequest() {
        return request;
    }


    public void setRequest(String request) {
        this.request = request;
    }
}