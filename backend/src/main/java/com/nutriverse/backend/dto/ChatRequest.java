package com.nutriverse.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    @NotBlank
    private String message;

    @NotBlank
    private String conversationId;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
