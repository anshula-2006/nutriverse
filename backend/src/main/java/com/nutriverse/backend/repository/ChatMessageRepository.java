package com.nutriverse.backend.repository;

import com.nutriverse.backend.model.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findTop10ByConversationIdOrderByTimestampDesc(String conversationId);
}
