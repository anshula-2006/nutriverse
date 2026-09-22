package com.nutriverse.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public class ChatResponse {

    private String reply;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RecommendationResponse recommendation;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CompositeMeal compositeMeal;


    public ChatResponse(String reply) {
        this.reply = reply;
    }


    public ChatResponse(
            String reply,
            RecommendationResponse recommendation
    ) {
        this.reply = reply;
        this.recommendation = recommendation;
    }


    public ChatResponse(
            String reply,
            CompositeMeal compositeMeal
    ) {
        this.reply = reply;
        this.compositeMeal = compositeMeal;
    }


    public String getReply() {
        return reply;
    }


    public void setReply(String reply) {
        this.reply = reply;
    }


    public RecommendationResponse getRecommendation() {
        return recommendation;
    }


    public CompositeMeal getCompositeMeal() {
        return compositeMeal;
    }


    // =========================================================
    // COMPOSITE / HOME-COOKED MEAL RESULT
    // =========================================================

    public static class CompositeMeal {

        private final String dishName;
        private final Integer servings;

        private final NutritionResult perServing;

        private final List<IngredientEvidence> ingredients;

        private final List<String> warnings;


        public CompositeMeal(
                String dishName,
                Integer servings,
                NutritionResult perServing,
                List<IngredientEvidence> ingredients,
                List<String> warnings
        ) {
            this.dishName = dishName;
            this.servings = servings;
            this.perServing = perServing;
            this.ingredients = ingredients;
            this.warnings = warnings;
        }


        public String getDishName() {
            return dishName;
        }


        public Integer getServings() {
            return servings;
        }


        public NutritionResult getPerServing() {
            return perServing;
        }


        public List<IngredientEvidence> getIngredients() {
            return ingredients;
        }


        public List<String> getWarnings() {
            return warnings;
        }
    }


    public static class IngredientEvidence {

        private final String ingredient;
        private final Double quantityGrams;

        private final String matchedFood;

        private final String source;
        private final String sourceId;


        public IngredientEvidence(
                String ingredient,
                Double quantityGrams,
                String matchedFood,
                String source,
                String sourceId
        ) {
            this.ingredient = ingredient;
            this.quantityGrams = quantityGrams;
            this.matchedFood = matchedFood;
            this.source = source;
            this.sourceId = sourceId;
        }


        public String getIngredient() {
            return ingredient;
        }


        public Double getQuantityGrams() {
            return quantityGrams;
        }


        public String getMatchedFood() {
            return matchedFood;
        }


        public String getSource() {
            return source;
        }


        public String getSourceId() {
            return sourceId;
        }
    }
}