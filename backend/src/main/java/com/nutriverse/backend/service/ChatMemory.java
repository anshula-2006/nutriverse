package com.nutriverse.backend.service;

import com.nutriverse.backend.model.ChatMessage;
import com.nutriverse.backend.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

@Service
public class ChatMemory {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMemory(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<Map<String, String>> getHistory(String conversationId) {

        List<ChatMessage> messages = new ArrayList<>(chatMessageRepository
                .findTop10ByConversationIdOrderByTimestampDesc(conversationId));
        Collections.reverse(messages);

        return messages.stream()
                .filter(message -> ("user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                        && message.getContent() != null && !message.getContent().isBlank())
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
