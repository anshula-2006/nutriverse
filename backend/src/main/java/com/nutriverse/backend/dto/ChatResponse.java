package com.nutriverse.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ChatResponse {
    private String reply;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RecommendationResponse recommendation;

    public ChatResponse(String reply) {
        this.reply = reply;
    }
    public ChatResponse(String reply, RecommendationResponse recommendation) {
        this.reply = reply;
        this.recommendation = recommendation;
    }
    public RecommendationResponse getRecommendation() {
        return recommendation;
    }
    public String getReply() {
        return reply;
    }
    public void setReply(String reply) {
        this.reply = reply;
    }
}
