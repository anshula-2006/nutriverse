
package com.nutriverse.backend.controller;

import com.nutriverse.backend.model.FoodNode;
import com.nutriverse.backend.repository.FoodNodeRepository;
import com.nutriverse.backend.service.IfctFoodDataProvider;
import com.nutriverse.backend.dto.NutritionResult;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/kg")
public class KnowledgeGraphController {

    private final FoodNodeRepository foodNodeRepository;
    private final IfctFoodDataProvider ifct;
    private final Neo4jClient neo4jClient;

    public KnowledgeGraphController(
            FoodNodeRepository foodNodeRepository,
            IfctFoodDataProvider ifct,
            Neo4jClient neo4jClient) {

        this.foodNodeRepository = foodNodeRepository;
        this.ifct = ifct;
        this.neo4jClient = neo4jClient;
    }

    @GetMapping("/foods")
    public List<FoodNode> getFoods(
            @RequestAttribute(value = "authenticatedRole",
                    required = false) String role) {

        requireAdmin(role);
        return foodNodeRepository.findAll();
    }

    @PostMapping("/import")
    public Map<String, Object> importFoods(
            @RequestAttribute(value = "authenticatedRole",
                    required = false) String role) {

        requireAdmin(role);

        List<NutritionResult> foods = ifct.getAllFoods();
        List<Map<String, Object>> rows = new ArrayList<>();

        for (NutritionResult food : foods) {

            Map<String, Object> row = new HashMap<>();

            row.put("name", food.getFoodName());
            row.put("sourceId", food.getSourceId());
            row.put("source", food.getSource());

            List<Map<String, Object>> nutrients = new ArrayList<>();

            addNutrient(nutrients, "Energy",
                    food.getCalories(), "kcal");

            addNutrient(nutrients, "Protein",
                    food.getProtein(), "g");

            addNutrient(nutrients, "Carbohydrates",
                    food.getCarbs(), "g");

            addNutrient(nutrients, "Fat",
                    food.getFat(), "g");

            addNutrient(nutrients, "Fiber",
                    food.getFiber(), "g");

            addNutrient(nutrients, "Iron",
                    food.getIron(), "mg");

            addNutrient(nutrients, "Calcium",
                    food.getCalcium(), "mg");

            addNutrient(nutrients, "Sodium",
                    food.getSodium(), "mg");

            addNutrient(nutrients, "Potassium",
                    food.getPotassium(), "mg");

            row.put("nutrients", nutrients);
            rows.add(row);
        }

        neo4jClient.query("""
            UNWIND $rows AS row

            MERGE (f:Food {
                source: row.source,
                sourceId: row.sourceId
            })

            SET f.name = row.name,
                f.verified = true,
                f.servingSize = 100.0,
                f.servingUnit = 'g'

            MERGE (s:Source {name: row.source})
            MERGE (f)-[:SOURCED_FROM]->(s)

            WITH f, row
            UNWIND row.nutrients AS nutrient

            MERGE (n:Nutrient {name: nutrient.name})
            MERGE (f)-[r:CONTAINS]->(n)

            SET r.amount = nutrient.amount,
                r.unit = nutrient.unit,
                r.perGrams = 100.0
            """)
            .bind(rows).to("rows")
            .run();

        return Map.of(
                "message", "IFCT graph import completed",
                "foodsProcessed", foods.size()
        );
    }

    private void addNutrient(
            List<Map<String, Object>> nutrients,
            String name, Double amount, String unit) {

        if (amount != null
                && Double.isFinite(amount)
                && amount >= 0) {

            nutrients.add(Map.of(
                    "name", name,
                    "amount", amount,
                    "unit", unit
            ));
        }
    }

    private void requireAdmin(String role) {

        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator access required"
            );
        }
    }
}
