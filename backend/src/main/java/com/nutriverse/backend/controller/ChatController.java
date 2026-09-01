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
            @Valid @RequestBody ChatRequest request) {

        String reply = groqService.getReply(
                request.getConversationId(),
                request.getMessage()
        );

        return new ChatResponse(reply);
    }
}
