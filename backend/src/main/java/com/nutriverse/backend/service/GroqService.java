package com.nutriverse.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private final RestClient restClient;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    public GroqService() {
        this.restClient = RestClient.create();
    }

    public String getReply(String userMessage) {

        Map<String, Object> requestBody = Map.of(
                "model", model,

                "messages", List.of(

                        Map.of(
                                "role", "system",
                                "content",
                                """
                                You are Nutri, a friendly AI nutrition companion.

                                Talk naturally like a supportive friend.
                                Keep responses short and conversational.

                                Do not overwhelm the user with many questions.
                                Ask only one relevant question at a time.

                                Help with meals, nutrition, recipes,
                                dietary preferences and food choices.

                                Never diagnose diseases or prescribe medicine.
                                """
                        ),

                        Map.of(
                                "role", "user",
                                "content", userMessage
                        )
                )
        );

        Map response = restClient
                .post()
                .uri(apiUrl)
                .header(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");

        Map<String, Object> firstChoice =
                choices.get(0);

        Map<String, Object> message =
                (Map<String, Object>) firstChoice.get("message");

        return (String) message.get("content");
    }
}