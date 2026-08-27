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

    // Temporary chat state only.
    // NOT saved in MongoDB.
    private final Map<String, ChatOnboardingState> states =
            new ConcurrentHashMap<>();


    public ProfileExtractionService(
            NutritionProfileRepository profileRepository
    ) {
        this.profileRepository = profileRepository;
    }


    // =========================================================
    // PROCESS USER MESSAGE
    // =========================================================

    public void processUserMessage(
            String userId,
            String message
    ) {

        if (userId == null ||
                message == null ||
                message.isBlank()) {
            return;
        }

        String text =
                normalize(message);

        ChatOnboardingState state =
                states.computeIfAbsent(
                        userId,
                        id -> new ChatOnboardingState()
                );

        NutritionProfile profile =
                profileRepository
                        .findByUserId(userId)
                        .orElseGet(
                                () -> new NutritionProfile(userId)
                        );

        boolean changed = false;


        // -----------------------------------------------------
        // 1. SHORT ANSWER BASED ON WHAT NUTRI ASKED
        // -----------------------------------------------------

        String awaitingField =
                state.getAwaitingField();

        if (awaitingField != null) {

            boolean saved =
                    saveAwaitedValue(
                            profile,
                            awaitingField,
                            text
                    );

            if (saved) {
                changed = true;
                state.setAwaitingField(null);
            }
        }


        // -----------------------------------------------------
        // 2. EXTRACT FROM NORMAL SENTENCES
        // -----------------------------------------------------

        String goal =
                extractGoal(text);

        if (goal != null) {
            profile.setGoal(goal);
            changed = true;
        }


        String diet =
                extractDiet(text);

        if (diet != null) {
            profile.setDietType(diet);
            changed = true;
        }


        Integer age =
                extractAge(text);

        if (age != null) {
            profile.setAge(age);
            changed = true;
        }


        Double height =
                extractHeight(text);

        if (height != null) {
            profile.setHeight(height);
            changed = true;
        }


        Double weight =
                extractWeight(text);

        if (weight != null) {
            profile.setWeight(weight);
            changed = true;
        }


        String gender =
                extractGender(text);

        if (gender != null) {
            profile.setGender(gender);
            changed = true;
        }


        String activity =
                extractActivity(text);

        if (activity != null) {
            profile.setActivityLevel(activity);
            changed = true;
        }


        // -----------------------------------------------------
        // 3. SAVE ONLY WHEN SOMETHING WAS FOUND
        // -----------------------------------------------------

        if (changed) {
            profileRepository.save(profile);

            System.out.println(
                    "Nutrition profile updated for user: "
                            + userId
            );
        }
    }


    // =========================================================
    // CHECK WHAT NUTRI ASKED
    // =========================================================

    public void processAssistantReply(
            String userId,
            String reply
    ) {

        if (userId == null ||
                reply == null ||
                reply.isBlank()) {
            return;
        }

        ChatOnboardingState state =
                states.computeIfAbsent(
                        userId,
                        id -> new ChatOnboardingState()
                );

        String text =
                normalize(reply);

        String field =
                detectAskedField(text);

        if (field != null) {

            state.setAwaitingField(field);
            state.setLastAskedField(field);

            System.out.println(
                    "Waiting for profile field: "
                            + field
            );
        }
    }


    // =========================================================
    // SAVE SHORT ANSWERS
    // =========================================================

    private boolean saveAwaitedValue(
            NutritionProfile profile,
            String field,
            String text
    ) {

        switch (field) {

            case "age" -> {

                Integer value =
                        firstInteger(text);

                if (value != null &&
                        value >= 10 &&
                        value <= 100) {

                    profile.setAge(value);
                    return true;
                }
            }


            case "height" -> {

                Double value =
                        firstNumber(text);

                if (value != null &&
                        value >= 100 &&
                        value <= 250) {

                    profile.setHeight(value);
                    return true;
                }
            }


            case "weight" -> {

                Double value =
                        firstNumber(text);

                if (value != null &&
                        value >= 25 &&
                        value <= 350) {

                    profile.setWeight(value);
                    return true;
                }
            }


            case "gender" -> {

                String value =
                        parseShortGender(text);

                if (value != null) {

                    profile.setGender(value);
                    return true;
                }
            }


            case "dietType" -> {

                String value =
                        extractDiet(text);

                if (value != null) {

                    profile.setDietType(value);
                    return true;
                }
            }


            case "activityLevel" -> {

                String value =
                        extractActivity(text);

                if (value != null) {

                    profile.setActivityLevel(value);
                    return true;
                }
            }
        }

        return false;
    }


    // =========================================================
    // WHAT DID NUTRI ASK?
    // =========================================================

    private String detectAskedField(
            String text
    ) {

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
                text.contains("how active")) {
            return "activityLevel";
        }

        if (text.contains("diet") ||
                text.contains("vegetarian") ||
                text.contains("vegan")) {
            return "dietType";
        }

        return null;
    }


    // =========================================================
    // GOAL
    // =========================================================

    private String extractGoal(
            String text
    ) {

        if (text.contains("lose weight") ||
                text.contains("losing weight") ||
                text.contains("weight loss") ||
                text.contains("reduce weight") ||
                text.contains("cut weight")) {

            return "WEIGHT_LOSS";
        }

        if (text.contains("gain weight") ||
                text.contains("weight gain") ||
                text.contains("put on weight")) {

            return "WEIGHT_GAIN";
        }

        if (text.contains("maintain weight") ||
                text.contains("maintain my weight")) {

            return "MAINTAIN_WEIGHT";
        }

        if (text.contains("eat healthier") ||
                text.contains("eat healthy") ||
                text.contains("healthy eating")) {

            return "HEALTHY_EATING";
        }

        if (text.contains("get fit") ||
                text.contains("improve fitness")) {

            return "FITNESS";
        }

        return null;
    }


    // =========================================================
    // DIET TYPE
    // =========================================================

    private String extractDiet(
            String text
    ) {

        String compact =
                text.replaceAll("[^a-z]", "");


        // Check NON-VEG before VEG
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
                compact.equals("veggie") ||
                compact.equals("vegetarian") ||
                text.contains("i am vegetarian") ||
                text.contains("i am a vegetarian") ||
                text.contains("i'm vegetarian") ||
                text.contains("i'm a vegetarian") ||
                text.contains("pure veg") ||
                text.contains("don't eat meat") ||
                text.contains("do not eat meat")) {

            return "VEGETARIAN";
        }


        return null;
    }


    // =========================================================
    // AGE
    // =========================================================

    private Integer extractAge(
            String text
    ) {

        Matcher matcher =
                Pattern.compile(
                        "(?:age is|years old|year old|i am|i'm)\\s*(\\d{1,2})"
                ).matcher(text);

        if (matcher.find()) {

            int value =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            if (value >= 10 &&
                    value <= 100) {

                return value;
            }
        }

        return null;
    }


    // =========================================================
    // HEIGHT
    // =========================================================

    private Double extractHeight(
            String text
    ) {

        Matcher matcher =
                Pattern.compile(
                        "(\\d{2,3}(?:\\.\\d+)?)\\s*cm"
                ).matcher(text);

        if (matcher.find()) {

            double value =
                    Double.parseDouble(
                            matcher.group(1)
                    );

            if (value >= 100 &&
                    value <= 250) {

                return value;
            }
        }

        return null;
    }


    // =========================================================
    // WEIGHT
    // =========================================================

    private Double extractWeight(
            String text
    ) {

        Matcher matcher =
                Pattern.compile(
                        "(\\d{2,3}(?:\\.\\d+)?)\\s*kg"
                ).matcher(text);

        if (matcher.find()) {

            double value =
                    Double.parseDouble(
                            matcher.group(1)
                    );

            if (value >= 25 &&
                    value <= 350) {

                return value;
            }
        }

        return null;
    }


    // =========================================================
    // GENDER
    // =========================================================

    private String extractGender(
            String text
    ) {

        if (text.contains("female") ||
                text.contains("i am a woman") ||
                text.contains("i'm a woman")) {

            return "FEMALE";
        }

        if (text.contains("male") ||
                text.contains("i am a man") ||
                text.contains("i'm a man")) {

            return "MALE";
        }

        return null;
    }


    private String parseShortGender(
            String text
    ) {

        String value =
                text.trim().toLowerCase();

        if (value.equals("f") ||
                value.equals("female") ||
                value.equals("woman")) {

            return "FEMALE";
        }

        if (value.equals("m") ||
                value.equals("male") ||
                value.equals("man")) {

            return "MALE";
        }

        return null;
    }


    // =========================================================
    // ACTIVITY
    // =========================================================

    private String extractActivity(
            String text
    ) {

        if (text.contains("sedentary")) {
            return "SEDENTARY";
        }

        if (text.contains("lightly active") ||
                text.equals("light") ||
                text.equals("lightly")) {

            return "LIGHTLY_ACTIVE";
        }

        if (text.contains("moderately active") ||
                text.equals("moderate") ||
                text.equals("moderately")) {

            return "MODERATELY_ACTIVE";
        }

        if (text.contains("very active") ||
                text.contains("highly active") ||
                text.equals("active")) {

            return "VERY_ACTIVE";
        }

        return null;
    }


    // =========================================================
    // NUMBER HELPERS
    // =========================================================

    private Integer firstInteger(
            String text
    ) {

        Matcher matcher =
                Pattern.compile("\\d+")
                        .matcher(text);

        if (matcher.find()) {
            return Integer.parseInt(
                    matcher.group()
            );
        }

        return null;
    }


    private Double firstNumber(
            String text
    ) {

        Matcher matcher =
                Pattern.compile(
                        "\\d+(?:\\.\\d+)?"
                ).matcher(text);

        if (matcher.find()) {
            return Double.parseDouble(
                    matcher.group()
            );
        }

        return null;
    }


    private String normalize(
            String text
    ) {

        return text
                .toLowerCase()
                .trim()
                .replace("’", "'");
    }
}