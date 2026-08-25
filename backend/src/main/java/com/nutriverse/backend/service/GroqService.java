package com.nutriverse.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private final RestClient restClient;
    private final ChatMemory chatMemory;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    public GroqService(ChatMemory chatMemory) {
        this.restClient = RestClient.create();
        this.chatMemory = chatMemory;
    }

    public String getReply(String conversationId, String userMessage) {

        // Create message list
        List<Map<String, String>> messages = new ArrayList<>();

        // System prompt
        messages.add(Map.of(
                "role", "system",
                "content", """
                        You are Nutri, a friendly AI nutrition companion.

                        Talk naturally like a supportive friend.
                        Keep responses short and conversational.

                        Do not overwhelm the user with many questions.
                        Ask only one relevant question at a time.

                        Help with meals, nutrition, recipes,
                        dietary preferences and food choices.

                        Never diagnose diseases or prescribe medicine.
                        """
        ));

        // Add previous messages from this conversation
        messages.addAll(
                chatMemory.getHistory(conversationId)
        );

        // Add current user message
        messages.add(Map.of(
                "role", "user",
                "content", userMessage
        ));

        // Request body sent to Groq
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages
        );

        // Call Groq API
        Map response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        // Extract choices
        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");

        Map<String, Object> firstChoice = choices.get(0);

        // Extract assistant message
        Map<String, Object> message =
                (Map<String, Object>) firstChoice.get("message");

        String reply = (String) message.get("content");

        // Save current conversation in memory
        chatMemory.addMessage(
                conversationId,
                "user",
                userMessage
        );

        chatMemory.addMessage(
                conversationId,
                "assistant",
                reply
        );

        return reply;
    }
}