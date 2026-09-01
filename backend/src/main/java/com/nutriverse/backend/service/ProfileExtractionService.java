package com.nutriverse.backend.service;

import com.nutriverse.backend.model.ChatOnboardingState;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProfileExtractionService {

    private final NutritionProfileRepository profileRepository;
    private final DailyTargetService dailyTargetService;

    private final Map<String, ChatOnboardingState> states =
            new ConcurrentHashMap<>();

    public ProfileExtractionService(
            NutritionProfileRepository profileRepository,
            DailyTargetService dailyTargetService) {

        this.profileRepository = profileRepository;
        this.dailyTargetService = dailyTargetService;
    }

    public void processUserMessage(
            String userId,
            String message) {

        if (userId == null || message == null || message.isBlank()) {
            return;
        }

        String text = normalize(message);

        ChatOnboardingState state =
                states.computeIfAbsent(
                        userId,
                        id -> new ChatOnboardingState()
                );

        NutritionProfile profile =
                profileRepository
                        .findByUserId(userId)
                        .orElseGet(() -> new NutritionProfile(userId));

        boolean changed = false;

        if (state.getAwaitingField() != null) {

            if (saveAwaitedValue(
                    profile,
                    state.getAwaitingField(),
                    text)) {

                changed = true;
                state.setAwaitingField(null);
            }
        }

        String goal = extractGoal(text);
        if (goal != null) {
            profile.setGoal(goal);
            changed = true;
        }

        String diet = extractDiet(text);
        if (diet != null) {
            profile.setDietType(diet);
            changed = true;
        }

        Integer age = extractAge(text);
        if (age != null) {
            profile.setAge(age);
            changed = true;
        }

        Double height = extractHeight(text);
        if (height != null) {
            profile.setHeight(height);
            changed = true;
        }

        Double weight = extractWeight(text);
        if (weight != null) {
            profile.setWeight(weight);
            changed = true;
        }

        String gender = extractGender(text);
        if (gender != null) {
            profile.setGender(gender);
            changed = true;
        }

        String activity = extractActivity(text);
        if (activity != null) {
            profile.setActivityLevel(activity);
            changed = true;
        }

        if (changed) {
            profileRepository.save(profile);
            dailyTargetService.calculateTargets(userId);
        }
    }

    public void processAssistantReply(
            String userId,
            String reply) {

        if (userId == null || reply == null || reply.isBlank()) {
            return;
        }

        ChatOnboardingState state =
                states.computeIfAbsent(
                        userId,
                        id -> new ChatOnboardingState()
                );

        String field =
                detectAskedField(
                        normalize(reply)
                );

        if (field != null) {
            state.setAwaitingField(field);
            state.setLastAskedField(field);
        }
    }

    private boolean saveAwaitedValue(
            NutritionProfile profile,
            String field,
            String text) {

        switch (field) {

            case "age" -> {
                Integer value = firstInteger(text);

                if (value != null && value >= 1 && value <= 120) {
                    profile.setAge(value);
                    return true;
                }
            }

            case "height" -> {
                Double value = firstNumber(text);

                if (value != null && value >= 50 && value <= 250) {
                    profile.setHeight(value);
                    return true;
                }
            }

            case "weight" -> {
                Double value = firstNumber(text);

                if (value != null && value >= 10 && value <= 500) {
                    profile.setWeight(value);
                    return true;
                }
            }

            case "gender" -> {
                String value = extractGender(text);

                if (value != null) {
                    profile.setGender(value);
                    return true;
                }
            }

            case "dietType" -> {
                String value = extractDiet(text);

                if (value != null) {
                    profile.setDietType(value);
                    return true;
                }
            }

            case "activityLevel" -> {
                String value = extractActivity(text);

                if (value != null) {
                    profile.setActivityLevel(value);
                    return true;
                }
            }
        }

        return false;
    }

    private String detectAskedField(String text) {

        if (!text.contains("?")) {
            return null;
        }

        if (text.contains("how old") ||
                text.contains("your age")) {
            return "age";
        }

        if (text.contains("your height") ||
                text.contains("how tall")) {
            return "height";
        }

        if (text.contains("your weight") ||
                text.contains("how much do you weigh") ||
                text.contains("current weight")) {
            return "weight";
        }

        if (text.contains("gender") ||
                text.contains("male or female")) {
            return "gender";
        }

        if (text.contains("activity level") ||
                text.contains("how active") ||
                text.contains("exercise")) {
            return "activityLevel";
        }

        if (text.contains("diet") ||
                text.contains("vegetarian") ||
                text.contains("vegan")) {
            return "dietType";
        }

        return null;
    }

    private String extractGoal(String text) {

        if (containsAny(
                text,
                "lose weight",
                "losing weight",
                "weight loss",
                "reduce weight",
                "cut weight")) {
            return "WEIGHT_LOSS";
        }

        if (containsAny(
                text,
                "gain weight",
                "weight gain",
                "put on weight")) {
            return "WEIGHT_GAIN";
        }

        if (containsAny(
                text,
                "maintain weight",
                "maintain my weight",
                "keep my weight")) {
            return "MAINTENANCE";
        }

        if (containsAny(
                text,
                "eat healthier",
                "eat healthy",
                "healthy eating")) {
            return "HEALTHY_EATING";
        }

        if (containsAny(
                text,
                "get fit",
                "improve fitness")) {
            return "FITNESS";
        }

        return null;
    }

    private String extractDiet(String text) {

        String compact =
                text.replaceAll("[^a-z]", "");

        if (compact.equals("nvg") ||
                compact.equals("nonveg") ||
                compact.equals("nonvegetarian") ||
                text.contains("non veg") ||
                text.contains("non-veg") ||
                text.contains("non vegetarian")) {
            return "NON_VEGETARIAN";
        }

        if (compact.equals("vegan") ||
                text.contains("i am vegan") ||
                text.contains("i'm vegan") ||
                text.contains("no animal products")) {
            return "VEGAN";
        }

        if (compact.equals("vg") ||
                compact.equals("veg") ||
                compact.equals("vegetarian") ||
                text.contains("i am vegetarian") ||
                text.contains("i'm vegetarian") ||
                text.contains("pure veg") ||
                text.contains("don't eat meat") ||
                text.contains("do not eat meat")) {
            return "VEGETARIAN";
        }

        return null;
    }

    private Integer extractAge(String text) {

        Matcher matcher =
                Pattern.compile(
                        "(?:age is|years old|year old|i am|i'm)\\s*(\\d{1,3})"
                ).matcher(text);

        if (!matcher.find()) {
            return null;
        }

        int value =
                Integer.parseInt(
                        matcher.group(1)
                );

        return value >= 1 && value <= 120
                ? value
                : null;
    }

    private Double extractHeight(String text) {

        Matcher matcher =
                Pattern.compile(
                        "(\\d{2,3}(?:\\.\\d+)?)\\s*cm"
                ).matcher(text);

        if (!matcher.find()) {
            return null;
        }

        double value =
                Double.parseDouble(
                        matcher.group(1)
                );

        return value >= 50 && value <= 250
                ? value
                : null;
    }

    private Double extractWeight(String text) {

        Matcher matcher =
                Pattern.compile(
                        "(\\d{2,3}(?:\\.\\d+)?)\\s*kg"
                ).matcher(text);

        if (!matcher.find()) {
            return null;
        }

        double value =
                Double.parseDouble(
                        matcher.group(1)
                );

        return value >= 10 && value <= 500
                ? value
                : null;
    }

    private String extractGender(String text) {

        if (containsAny(
                text,
                "female",
                "woman")) {
            return "FEMALE";
        }

        if (containsAny(
                text,
                "male",
                "man")) {
            return "MALE";
        }

        if (containsAny(
                text,
                "other",
                "non binary",
                "non-binary")) {
            return "OTHER";
        }

        return null;
    }

    private String extractActivity(String text) {

        if (containsAny(
                text,
                "sedentary",
                "mostly sitting",
                "sit most of the day",
                "rarely exercise",
                "no exercise")) {
            return "SEDENTARY";
        }

        if (containsAny(
                text,
                "lightly active",
                "light activity")) {
            return "LIGHTLY_ACTIVE";
        }

        if (containsAny(
                text,
                "moderately active",
                "moderate activity")) {
            return "MODERATELY_ACTIVE";
        }

        if (containsAny(
                text,
                "very active",
                "highly active")) {
            return "VERY_ACTIVE";
        }

        if (containsAny(
                text,
                "extra active",
                "extremely active")) {
            return "EXTRA_ACTIVE";
        }

        Matcher matcher =
                Pattern.compile(
                        "(\\d)\\s*(?:days?|times?)\\s*(?:a|per)?\\s*week"
                ).matcher(text);

        if (matcher.find()) {

            int days =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            if (days <= 2) {
                return "LIGHTLY_ACTIVE";
            }

            if (days <= 5) {
                return "MODERATELY_ACTIVE";
            }

            if (days <= 7) {
                return "VERY_ACTIVE";
            }
        }

        if (containsAny(
                text,
                "once a week",
                "once per week",
                "weekly once",
                "twice a week",
                "twice per week")) {
            return "LIGHTLY_ACTIVE";
        }

        if (containsAny(
                text,
                "every day",
                "exercise daily",
                "workout daily",
                "work out daily")) {
            return "VERY_ACTIVE";
        }

        return null;
    }

    private Integer firstInteger(String text) {

        Matcher matcher =
                Pattern.compile("\\d+")
                        .matcher(text);

        return matcher.find()
                ? Integer.parseInt(matcher.group())
                : null;
    }

    private Double firstNumber(String text) {

        Matcher matcher =
                Pattern.compile(
                        "\\d+(?:\\.\\d+)?"
                ).matcher(text);

        return matcher.find()
                ? Double.parseDouble(matcher.group())
                : null;
    }

    private boolean containsAny(
            String text,
            String... values) {

        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String text) {
        return text
                .toLowerCase()
                .trim()
                .replace("’", "'");
    }
}