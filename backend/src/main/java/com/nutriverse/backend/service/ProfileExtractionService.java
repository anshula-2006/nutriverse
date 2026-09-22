package com.nutriverse.backend.service;

import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProfileExtractionService {

    private static final Pattern DISLIKE = Pattern.compile(
            "\\b(?:i don't like|i do not like|i dislike|i hate|"
                    + "i cannot eat|i can't eat|avoid|exclude)\\s+([^.!?]{1,100})",
            Pattern.CASE_INSENSITIVE
    );

    private final NutritionProfileRepository repository;
    private final DailyTargetService dailyTargetService;

    // Temporary conversation state only
    private final Map<String, String> awaitingFields =
            new ConcurrentHashMap<>();

    public ProfileExtractionService(
            NutritionProfileRepository repository,
            DailyTargetService dailyTargetService
    ) {
        this.repository = repository;
        this.dailyTargetService = dailyTargetService;
    }

    public void processUserMessage(
            String userId,
            String message
    ) {

        if (userId == null ||
                message == null ||
                message.isBlank())
            return;

        String text = normalize(message);
        String awaited = awaitingFields.remove(userId);

        NutritionProfile profile = repository
                .findByUserId(userId)
                .orElseGet(() ->
                        new NutritionProfile(userId)
                );

        boolean changed = false;

        Integer age = age(
                text,
                "age".equals(awaited)
        );

        if (age != null) {
            profile.setAge(age);
            changed = true;
        }

        Double height = measurement(
                text,
                "height",
                "cm",
                50,
                250,
                "height".equals(awaited)
        );

        if (height != null) {
            profile.setHeight(height);
            changed = true;
        }

        Double weight = measurement(
                text,
                "weight",
                "kg",
                10,
                500,
                "weight".equals(awaited)
        );

        if (weight != null) {
            profile.setWeight(weight);
            changed = true;
        }

        String gender = choice(
                text,
                "(?:my gender is|i am|i'm)\\s+(?:a\\s+)?",
                Map.of(
                        "female", "FEMALE",
                        "woman", "FEMALE",
                        "male", "MALE",
                        "man", "MALE",
                        "other", "OTHER",
                        "non binary", "OTHER",
                        "non-binary", "OTHER"
                )
        );

        if (gender != null) {
            profile.setGender(gender);
            changed = true;
        }

        String diet = choice(
                text,
                "(?:my diet is|i am|i'm)\\s+(?:a\\s+)?",
                Map.of(
                        "vegetarian", "VEGETARIAN",
                        "veg", "VEGETARIAN",
                        "vegan", "VEGAN",
                        "non vegetarian", "NON_VEGETARIAN",
                        "non-vegetarian", "NON_VEGETARIAN",
                        "non veg", "NON_VEGETARIAN",
                        "non-veg", "NON_VEGETARIAN"
                )
        );

        if (diet != null) {
            profile.setDietType(diet);
            changed = true;
        }

        String activity = choice(
                text,
                "(?:my activity level is|i am|i'm)\\s+",
                Map.of(
                        "sedentary", "SEDENTARY",
                        "lightly active", "LIGHTLY_ACTIVE",
                        "moderately active", "MODERATELY_ACTIVE",
                        "very active", "VERY_ACTIVE",
                        "extra active", "EXTRA_ACTIVE"
                )
        );

        if (activity == null) {
            activity = exerciseActivity(
                    text,
                    "activityLevel".equals(awaited)
            );
        }

        if (activity != null) {
            profile.setActivityLevel(activity);
            changed = true;
        }

        String goal = choice(
                text,
                "(?:my goal is to|my goal is|i want to|i would like to)\\s+",
                Map.of(
                        "lose weight", "WEIGHT_LOSS",
                        "gain weight", "WEIGHT_GAIN",
                        "maintain weight", "MAINTENANCE",
                        "maintain my weight", "MAINTENANCE",
                        "eat healthier", "HEALTHY_EATING",
                        "eat healthy", "HEALTHY_EATING",
                        "get fit", "FITNESS",
                        "improve fitness", "FITNESS"
                )
        );

        if (goal != null) {
            profile.setGoal(goal);
            changed = true;
        }

        // Medical dietary restriction
        if (declaresGlutenRestriction(text) &&
                !"GLUTEN_FREE".equals(
                        profile.getDietaryRestriction()
                )) {

            profile.setDietaryRestriction(
                    "GLUTEN_FREE"
            );

            changed = true;
        }

        // Explicit cultural / religious food preference
        String religiousDiet =
                extractReligiousDiet(text);

        if (religiousDiet != null &&
                !religiousDiet.equals(
                        profile.getReligiousDiet()
                )) {

            profile.setReligiousDiet(
                    religiousDiet
            );

            changed = true;
        }

        List<String> dislikes =
                extractDislikes(text);

        if (!dislikes.isEmpty()) {

            String merged = mergeCsv(
                    profile.getFoodDislikes(),
                    dislikes,
                    300
            );

            if (!merged.equals(
                    profile.getFoodDislikes()
            )) {

                profile.setFoodDislikes(merged);
                changed = true;
            }
        }

        if (changed) {
            repository.save(profile);
            dailyTargetService.calculateTargets(userId);
        }
    }

    public void processAssistantReply(
            String userId,
            String reply
    ) {

        if (userId == null)
            return;

        awaitingFields.remove(userId);

        if (reply == null ||
                !reply.contains("?"))
            return;

        String text = normalize(reply);
        String field = null;

        if (text.contains("how old") ||
                text.contains("your age")) {

            field = "age";

        } else if (text.contains("your height") ||
                text.contains("how tall")) {

            field = "height";

        } else if (text.contains("your weight") ||
                text.contains("how much do you weigh")) {

            field = "weight";

        } else if (text.contains("your activity level") ||
                text.contains("how active") ||
                (text.contains("how many days") &&
                        text.contains("exercise"))) {

            field = "activityLevel";
        }

        if (field != null) {

            if (awaitingFields.size() >= 1000)
                awaitingFields.clear();

            awaitingFields.put(userId, field);
        }
    }

    // Used when chat history is deleted.
    // Saved profile values remain untouched.
    public void clearPendingState(String userId) {

        if (userId != null) {
            awaitingFields.remove(userId);
        }
    }

    private String extractReligiousDiet(
            String text
    ) {

        if (text.matches(
                ".*\\b(?:follow|eat|prefer|want|need|only eat)\\s+"
                        + "(?:a\\s+)?jain(?:\\s+diet|\\s+food)?.*"
        )) {
            return "JAIN";
        }

        if (text.matches(
                ".*\\b(?:follow|eat|prefer|want|need|only eat)\\s+"
                        + "(?:a\\s+)?halal(?:\\s+diet|\\s+food)?.*"
        )) {
            return "HALAL";
        }

        if (text.contains("keep kosher") ||
                text.matches(
                        ".*\\b(?:follow|eat|prefer|want|need|only eat)\\s+"
                                + "(?:a\\s+)?kosher(?:\\s+diet|\\s+food)?.*"
                )) {
            return "KOSHER";
        }

        return null;
    }

    private boolean declaresGlutenRestriction(
            String text
    ) {

        if (text.contains("don't have celiac") ||
                text.contains("do not have celiac") ||
                text.contains("not celiac")) {

            return false;
        }

        return text.contains("i have celiac")
                || text.contains("i have coeliac")
                || text.contains("celiac disease")
                || text.contains("coeliac disease")
                || text.contains("gluten free")
                || text.contains("gluten-free")
                || text.contains("can't eat gluten")
                || text.contains("cannot eat gluten")
                || text.contains("avoid gluten");
    }

    private List<String> extractDislikes(
            String text
    ) {

        Matcher matcher =
                DISLIKE.matcher(text);

        if (!matcher.find())
            return List.of();

        String value = matcher.group(1)
                .replaceAll(
                        "\\b(?:please|anymore|right now)\\b",
                        ""
                )
                .trim();

        if (value.isEmpty())
            return List.of();

        List<String> result =
                new ArrayList<>();

        for (String part :
                value.split(
                        "\\s*(?:,|\\bor\\b|\\band\\b)\\s*"
                )) {

            String food = part.trim();

            if (food.length() < 2 ||
                    food.length() > 50)
                continue;

            // Gluten is stored as a restriction,
            // not as an ordinary dislike.
            if ("gluten".equals(food) ||
                    food.contains("celiac") ||
                    food.contains("coeliac"))
                continue;

            result.add(food);
        }

        return result;
    }

    private String mergeCsv(
            String existing,
            List<String> additions,
            int maxLength
    ) {

        Set<String> values =
                new LinkedHashSet<>();

        if (existing != null &&
                !existing.isBlank()) {

            for (String item :
                    existing.split(",")) {

                String value = item.trim();

                if (!value.isEmpty())
                    values.add(value);
            }
        }

        values.addAll(additions);

        StringBuilder output =
                new StringBuilder();

        for (String value : values) {

            String next =
                    output.isEmpty()
                            ? value
                            : ", " + value;

            if (output.length() +
                    next.length() > maxLength)
                break;

            output.append(next);
        }

        return output.toString();
    }

    private Integer age(
            String text,
            boolean awaited
    ) {

        String value = match(
                text,
                "\\b(?:i am|i'm)\\s+(?:a\\s+)?"
                        + "(\\d{1,3})\\s*(?:years? old|y/o)\\b"
        );

        if (value == null) {
            value = match(
                    text,
                    "\\bmy age(?: is|:)?\\s+"
                            + "(\\d{1,3})(?![\\d.])\\b"
            );
        }

        if (value == null && awaited) {
            value = match(
                    text,
                    "^(?:i am |i'm )?"
                            + "(\\d{1,3})"
                            + "(?: years? old)?[.!]?$"
            );
        }

        if (value == null)
            return null;

        int age =
                Integer.parseInt(value);

        return age >= 1 && age <= 120
                ? age
                : null;
    }

    private Double measurement(
            String text,
            String field,
            String unit,
            double min,
            double max,
            boolean awaited
    ) {

        String prefix =
                "weight".equals(field)
                        ? "(?:i weigh|my weight is|i am|i'm)"
                        : "(?:my height is|i am|i'm)";

        String value = match(
                text,
                "\\b" + prefix
                        + "\\s+(\\d{2,3}(?:\\.\\d{1,2})?)"
                        + "\\s*" + unit + "\\b"
        );

        if (value == null) {
            value = match(
                    text,
                    "^(\\d{2,3}(?:\\.\\d{1,2})?)"
                            + "\\s*" + unit + "[.!]?$"
            );
        }

        if (value == null && awaited) {
            value = match(
                    text,
                    "^(\\d{2,3}(?:\\.\\d{1,2})?)[.!]?$"
            );
        }

        if (value == null)
            return null;

        double result =
                Double.parseDouble(value);

        return result >= min &&
                result <= max
                ? result
                : null;
    }

    private String choice(
            String text,
            String prefix,
            Map<String, String> choices
    ) {

        List<String> options =
                choices.keySet()
                        .stream()
                        .sorted(
                                (a, b) ->
                                        Integer.compare(
                                                b.length(),
                                                a.length()
                                        )
                        )
                        .toList();

        for (String option : options) {

            String quoted =
                    Pattern.quote(option);

            if (text.matches(
                    "^" + quoted + "[.!]?$"
            ) ||
                    Pattern.compile(
                                    "\\b"
                                            + prefix
                                            + quoted
                                            + "\\b"
                            )
                            .matcher(text)
                            .find()) {

                return choices.get(option);
            }
        }

        return null;
    }

    private String exerciseActivity(
            String text,
            boolean awaited
    ) {

        String days = match(
                text,
                "\\bi (?:exercise|work out|workout|train)"
                        + "\\s+([0-7])\\s*"
                        + "(?:days?|times?) (?:a|per) week\\b"
        );

        if (days == null && awaited) {
            days = match(
                    text,
                    "^([0-7])(?: (?:days?|times?)"
                            + " (?:a|per) week)?[.!]?$"
            );
        }

        if (days == null)
            return null;

        int count =
                Integer.parseInt(days);

        if (count == 0)
            return "SEDENTARY";

        if (count <= 2)
            return "LIGHTLY_ACTIVE";

        if (count <= 5)
            return "MODERATELY_ACTIVE";

        return "VERY_ACTIVE";
    }

    private String match(
            String text,
            String regex
    ) {

        Matcher matcher =
                Pattern.compile(regex)
                        .matcher(text);

        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    private String normalize(String text) {

        return text.toLowerCase(Locale.ROOT)
                .trim()
                .replace('\u2019', '\'')
                .replace('\u2011', '-');
    }
}