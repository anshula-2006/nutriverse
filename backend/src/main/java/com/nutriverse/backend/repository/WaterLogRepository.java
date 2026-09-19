package com.nutriverse.backend.repository;

import com.nutriverse.backend.model.WaterLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface WaterLogRepository
        extends MongoRepository<WaterLog, String> {

    @Query("{ 'userId': ?0, 'loggedAt': { '$gte': ?1, '$lt': ?2 } }")
    List<WaterLog> findByUserIdAndLoggedAtBetween(
            String userId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<WaterLog> findByUserIdOrderByLoggedAtDesc(String userId);
}
