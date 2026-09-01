package com.nutriverse.backend.repository;

import com.nutriverse.backend.model.FoodNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface FoodNodeRepository
        extends Neo4jRepository<FoodNode, String> {
}
