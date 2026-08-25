package com.nutriverse.backend.repository;

import com.nutriverse.backend.model.MealLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MealLogRepository
        extends MongoRepository<MealLog, String> {

    List<MealLog> findByUserIdOrderByLoggedAtDesc(String userId);

    List<MealLog> findByUserIdAndLoggedAtBetween(
            String userId,
            LocalDateTime start,
            LocalDateTime end
    );
}