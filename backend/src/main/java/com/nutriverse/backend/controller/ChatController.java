package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.ChatHistoryItem;
import com.nutriverse.backend.dto.ChatRequest;
import com.nutriverse.backend.dto.ChatResponse;
import com.nutriverse.backend.service.GroqService;
import com.nutriverse.backend.service.RecommendationService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GroqService groqService;
    private final RecommendationService recommendationService;

    public ChatController(GroqService groqService, RecommendationService recommendationService) {
        this.groqService = groqService;
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public List<ChatHistoryItem> history(
            @RequestAttribute("authenticatedUserId") String userId) {
        return recommendationService.getChatHistory(userId);
    }

    @PostMapping
    public ChatResponse chat(
            @RequestAttribute("authenticatedUserId") String userId,
            @Valid @RequestBody ChatRequest request) {

        if (request.getSourceId() == null
                && recommendationService.isStructuredFollowup(userId, request.getMessage())) {
            return new ChatResponse("Here are additional verified options.",
                    recommendationService.recommend(userId, request.getMessage()));
        }

        String reply = request.getSourceId() == null ? groqService.getReply(
                userId,
                request.getMessage()
        ) : groqService.getReply(userId, request.getMessage(), request.getSource(), request.getSourceId());

        return new ChatResponse(reply);
    }
}
