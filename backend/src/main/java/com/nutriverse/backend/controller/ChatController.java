package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.ChatHistoryItem;
import com.nutriverse.backend.dto.ChatRequest;
import com.nutriverse.backend.dto.ChatResponse;
import com.nutriverse.backend.service.*;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GroqService groq;
    private final RecommendationService recommendations;
    private final CompositeMealService compositeMeals;

    @Autowired
    public ChatController(
            GroqService groq,
            RecommendationService recommendations,
            CompositeMealService compositeMeals
    ) {
        this.groq = groq;
        this.recommendations = recommendations;
        this.compositeMeals = compositeMeals;
    }

    // Existing tests use this constructor.
    public ChatController(
            GroqService groq,
            RecommendationService recommendations
    ) {
        this.groq = groq;
        this.recommendations = recommendations;
        this.compositeMeals = null;
    }

    @GetMapping
    public List<ChatHistoryItem> history(
            @RequestAttribute("authenticatedUserId") String userId
    ) {
        return recommendations.getChatHistory(userId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(
            @RequestAttribute("authenticatedUserId") String userId
    ) {
        recommendations.clearChatHistory(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ChatResponse chat(
            @RequestAttribute("authenticatedUserId") String userId,
            @Valid @RequestBody ChatRequest request
    ) {
        String message = request.getMessage();

        // Exact Food Search selection.
        if (request.getSourceId() != null) {
            return new ChatResponse(groq.getReply(
                    userId,
                    message,
                    request.getSource(),
                    request.getSourceId()
            ));
        }

        // Generate a recipe before homemade-meal routing.
        if (GroqService.isRecipeRequest(message)
                && !GroqService.isQuantitative(message)) {
            return new ChatResponse(
                    groq.getReply(userId, message)
            );
        }

        // Nutrition for a recipe already generated in chat.
        if (groq.hasRecipeNutritionContext(userId, message)) {
            return new ChatResponse(
                    groq.getReply(userId, message)
            );
        }

        // "Another option", "something else", etc.
        if (recommendations.isStructuredFollowup(userId, message)) {
            return new ChatResponse(
                    "Here are additional verified options.",
                    recommendations.recommend(userId, message)
            );
        }

        // User-entered homemade recipe calculation.
        if (compositeMeals != null) {
            ChatResponse result = compositeMeals.handle(userId, message);
            if (result != null) return result;
        }

        return new ChatResponse(
                groq.getReply(userId, message)
        );
    }
}