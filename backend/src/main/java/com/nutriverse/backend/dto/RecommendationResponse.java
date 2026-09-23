package com.nutriverse.backend.dto;

import java.util.List;

public class RecommendationResponse {

    private String explanationMethod;
    private String interpretedDiet;
    private String interpretedGoal;
    private List<RecommendationItem> recommendations;

    public RecommendationResponse() {
    }

    public RecommendationResponse(
            String explanationMethod,
            String interpretedDiet,
            String interpretedGoal,
            List<RecommendationItem> recommendations
    ) {
        this.explanationMethod = explanationMethod;
        this.interpretedDiet = interpretedDiet;
        this.interpretedGoal = interpretedGoal;
        this.recommendations = recommendations;
    }

    public String getExplanationMethod() {
        return explanationMethod;
    }

    public void setExplanationMethod(String explanationMethod) {
        this.explanationMethod = explanationMethod;
    }

    public String getInterpretedDiet() {
        return interpretedDiet;
    }

    public void setInterpretedDiet(String interpretedDiet) {
        this.interpretedDiet = interpretedDiet;
    }

    public String getInterpretedGoal() {
        return interpretedGoal;
    }

    public void setInterpretedGoal(String interpretedGoal) {
        this.interpretedGoal = interpretedGoal;
    }

    public List<RecommendationItem> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<RecommendationItem> recommendations) {
        this.recommendations = recommendations;
    }

    public static class RecommendationItem {

        private String what;
        private List<String> why;
        private Evidence evidence;
        private String reason;

        public RecommendationItem() {
        }

        public RecommendationItem(
                String what,
                List<String> why,
                Evidence evidence,
                String reason
        ) {
            this.what = what;
            this.why = why;
            this.evidence = evidence;
            this.reason = reason;
        }

        public String getWhat() {
            return what;
        }

        public void setWhat(String what) {
            this.what = what;
        }

        public List<String> getWhy() {
            return why;
        }

        public void setWhy(List<String> why) {
            this.why = why;
        }

        public Evidence getEvidence() {
            return evidence;
        }

        public void setEvidence(Evidence evidence) {
            this.evidence = evidence;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class Evidence {

        private Double servingSize;
        private String servingUnit;
        private Double calories;
        private Double protein;
        private Double fiber;
        private String source;
        private String sourceId;
        private String dataType;
        private boolean verified;

        public Evidence() {
        }

        public Evidence(
                Double servingSize,
                String servingUnit,
                Double calories,
                Double protein,
                Double fiber,
                String source,
                String sourceId,
                String dataType,
                boolean verified
        ) {
            this.servingSize = servingSize;
            this.servingUnit = servingUnit;
            this.calories = calories;
            this.protein = protein;
            this.fiber = fiber;
            this.source = source;
            this.sourceId = sourceId;
            this.dataType = dataType;
            this.verified = verified;
        }

        public Double getServingSize() {
            return servingSize;
        }

        public void setServingSize(Double servingSize) {
            this.servingSize = servingSize;
        }

        public String getServingUnit() {
            return servingUnit;
        }

        public void setServingUnit(String servingUnit) {
            this.servingUnit = servingUnit;
        }

        public Double getCalories() {
            return calories;
        }

        public void setCalories(Double calories) {
            this.calories = calories;
        }

        public Double getProtein() {
            return protein;
        }

        public void setProtein(Double protein) {
            this.protein = protein;
        }

        public Double getFiber() {
            return fiber;
        }

        public void setFiber(Double fiber) {
            this.fiber = fiber;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getSourceId() {
            return sourceId;
        }

        public void setSourceId(String sourceId) {
            this.sourceId = sourceId;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public boolean isVerified() {
            return verified;
        }

        public void setVerified(boolean verified) {
            this.verified = verified;
        }
    }
}