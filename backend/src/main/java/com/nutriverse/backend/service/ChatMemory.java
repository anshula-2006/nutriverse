package com.nutriverse.backend.service;

import com.nutriverse.backend.model.ChatMessage;
import com.nutriverse.backend.dto.RecommendationResponse;
import com.nutriverse.backend.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ChatMemory {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMemory(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<Map<String, String>> getHistory(String conversationId) {
        return getRecentMessages(conversationId).stream()
                .map(message -> Map.of(
                        "role", message.getRole(),
                        "content", message.getContent()
                ))
                .toList();
    }

    public List<ChatMessage> getRecentMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }

        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository
                        .findTop10ByConversationIdOrderByTimestampDesc(conversationId)
        );

        Collections.reverse(messages);

        return messages.stream()
                .filter(message ->
                        ("user".equals(message.getRole())
                                || "assistant".equals(message.getRole()))
                                && message.getContent() != null
                                && !message.getContent().isBlank()
                )
                .toList();
    }

    public List<ChatMessage> getRecentRecommendations(String conversationId) {
        return getRecentMessages(conversationId).stream()
                .filter(message -> "assistant".equals(message.getRole())
                        && message.getRecommendation() != null
                        && message.getRecommendationRequest() != null)
                .toList();
    }

    public void addRecommendation(String conversationId, String request, String resolvedRequest,
                                  RecommendationResponse response) {
        addMessage(conversationId, "user", request);
        StringBuilder content = new StringBuilder("Recommendation request: ").append(resolvedRequest);
        for (var item : response.getRecommendations()) {
            content.append("\n\nRecommendation: ").append(item.getWhat())
                    .append("\nWhy this fits you: ").append(String.join(" ", item.getWhy()))
                    .append("\nReason: ").append(item.getReason());
        }
        if (response.getRecommendations().isEmpty()) {
            content.append("\nNo additional verified options matched your constraints.");
        }
        ChatMessage message = new ChatMessage(conversationId, "assistant", content.toString());
        message.setRecommendationRequest(resolvedRequest);
        message.setRecommendation(response);
        chatMessageRepository.save(message);
    }

    public void addMessage(
            String conversationId,
            String role,
            String content
    ) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        if (role == null || role.isBlank()) {
            return;
        }

        if (content == null || content.isBlank()) {
            return;
        }

        ChatMessage message = new ChatMessage(
                conversationId,
                role,
                content.trim()
        );

        chatMessageRepository.save(message);
    }
}
