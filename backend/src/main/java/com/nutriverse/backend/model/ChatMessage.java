package com.nutriverse.backend.model;

import com.nutriverse.backend.dto.RecommendationResponse;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "chat_messages")
public class ChatMessage {

    @Id
    private String id;

    private String conversationId;
    private String role;
    private String content;
    private Instant timestamp;
    private String recommendationRequest;
    private RecommendationResponse recommendation;

    public ChatMessage() {
    }

    public ChatMessage(String conversationId, String role, String content) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getRecommendationRequest() {
        return recommendationRequest;
    }

    public void setRecommendationRequest(String recommendationRequest) {
        this.recommendationRequest = recommendationRequest;
    }

    public RecommendationResponse getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(RecommendationResponse recommendation) {
        this.recommendation = recommendation;
    }
}
