package com.nutriverse.backend.controller;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TestController {

    private final MongoTemplate mongoTemplate;

    public TestController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/test")
    public String test() {
        return "NutriVerse backend is working!";
    }

    @GetMapping("/dbtest")
    public String dbTest() {
        return "Connected database: " + mongoTemplate.getDb().getName()
                + " | Collections: " +  mongoTemplate.getCollectionNames();
    }
}