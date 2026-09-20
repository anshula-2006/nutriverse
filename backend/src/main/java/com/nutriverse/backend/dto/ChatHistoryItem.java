package com.nutriverse.backend.dto;

public record ChatHistoryItem(
        String role,
        String content,
        RecommendationResponse recommendation
) {
}
