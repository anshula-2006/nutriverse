package com.nutriverse.backend.service;

import com.nutriverse.backend.model.ChatMessage;
import com.nutriverse.backend.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatMemory {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMemory(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<Map<String, String>> getHistory(String conversationId) {

        List<ChatMessage> messages =
                chatMessageRepository
                        .findByConversationIdOrderByTimestampAsc(conversationId);

        // Keep only latest 10 messages
        if (messages.size() > 10) {
            messages = messages.subList(
                    messages.size() - 10,
                    messages.size()
            );
        }

        return messages.stream()
                .map(message -> Map.of(
                        "role", message.getRole(),
                        "content", message.getContent()
                ))
                .toList();
    }

    public void addMessage(
            String conversationId,
            String role,
            String content
    ) {

        ChatMessage message =
                new ChatMessage(
                        conversationId,
                        role,
                        content
                );

        chatMessageRepository.save(message);
    }
}