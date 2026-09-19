package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.ChatRequest;
import com.nutriverse.backend.dto.ChatResponse;
import com.nutriverse.backend.service.GroqService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GroqService groqService;

    public ChatController(GroqService groqService) {
        this.groqService = groqService;
    }

    @PostMapping
    public ChatResponse chat(
            @RequestAttribute("authenticatedUserId") String userId,
            @Valid @RequestBody ChatRequest request) {

        String reply = request.getSourceId() == null ? groqService.getReply(
                userId,
                request.getMessage()
        ) : groqService.getReply(userId, request.getMessage(), request.getSource(), request.getSourceId());

        return new ChatResponse(reply);
    }
}
