package com.nutriverse.backend.repository;

import com.nutriverse.backend.model.NutritionProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NutritionProfileRepository
        extends MongoRepository<NutritionProfile, String> {

    Optional<NutritionProfile> findByUserId(String userId);
}