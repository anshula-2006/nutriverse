package com.nutriverse.backend.repository;

import com.nutriverse.backend.model.MealLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MealLogRepository
        extends MongoRepository<MealLog, String> {

    List<MealLog> findByUserIdOrderByLoggedAtDesc(String userId);

    @Query("{ 'userId': ?0, 'loggedAt': { '$gte': ?1, '$lt': ?2 } }")
    List<MealLog> findByUserIdAndLoggedAtBetween(
            String userId,
            LocalDateTime start,
            LocalDateTime end
    );
}
