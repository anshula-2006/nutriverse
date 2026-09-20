package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.RecommendationRequest;
import com.nutriverse.backend.dto.RecommendationResponse;
import com.nutriverse.backend.service.RecommendationService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService
            recommendationService;


    public RecommendationController(
            RecommendationService recommendationService
    ) {

        this.recommendationService =
                recommendationService;
    }


    @PostMapping
    public RecommendationResponse recommend(
            @RequestAttribute(
                    "authenticatedUserId"
            )
            String userId,

            @Valid
            @RequestBody
            RecommendationRequest request
    ) {

        return recommendationService
                .recommend(
                        userId,
                        request.getRequest()
                );
    }
}