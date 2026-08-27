package com.nutriverse.backend.model;

public class ChatOnboardingState {

    private String awaitingField;
    private String lastAskedField;
    private int normalRepliesSinceLastQuestion;

    public ChatOnboardingState() {
        this.normalRepliesSinceLastQuestion = 0;
    }

    public String getAwaitingField() {
        return awaitingField;
    }

    public void setAwaitingField(String awaitingField) {
        this.awaitingField = awaitingField;
    }

    public String getLastAskedField() {
        return lastAskedField;
    }

    public void setLastAskedField(String lastAskedField) {
        this.lastAskedField = lastAskedField;
    }

    public int getNormalRepliesSinceLastQuestion() {
        return normalRepliesSinceLastQuestion;
    }

    public void setNormalRepliesSinceLastQuestion(
            int normalRepliesSinceLastQuestion
    ) {
        this.normalRepliesSinceLastQuestion =
                normalRepliesSinceLastQuestion;
    }
}