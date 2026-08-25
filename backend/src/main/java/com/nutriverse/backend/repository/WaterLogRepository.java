package com.nutriverse.backend.repository;

import com.nutriverse.backend.model.WaterLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WaterLogRepository
        extends MongoRepository<WaterLog, String> {

    List<WaterLog> findByUserIdAndLoggedAtBetween(
            String userId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<WaterLog> findByUserIdOrderByLoggedAtDesc(String userId);
}