package com.nutriverse.backend.controller;

import com.nutriverse.backend.model.FoodNode;
import com.nutriverse.backend.repository.FoodNodeRepository;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/kg")
public class KnowledgeGraphController {

    private final FoodNodeRepository foodNodeRepository;

    public KnowledgeGraphController(
            FoodNodeRepository foodNodeRepository) {

        this.foodNodeRepository =
                foodNodeRepository;
    }

    @GetMapping("/foods")
    public List<FoodNode> getFoods(@RequestAttribute(value = "authenticatedRole", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
        }

        return foodNodeRepository
                .findAll();
    }
}
