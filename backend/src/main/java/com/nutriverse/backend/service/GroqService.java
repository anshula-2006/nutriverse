package com.nutriverse.backend.service;

import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private static final Logger logger =
            LoggerFactory.getLogger(GroqService.class);

    private static final int HISTORY_LIMIT = 6;
    private static final int MAX_COMPLETION_TOKENS = 350;

    private final ChatMemory chatMemory;
    private final ProfileExtractionService profileExtractionService;
    private final NutritionProfileRepository profileRepository;
    private final RestClient restClient;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    public GroqService(
            ChatMemory chatMemory,
            ProfileExtractionService profileExtractionService,
            NutritionProfileRepository profileRepository
    ) {
        this.chatMemory = chatMemory;
        this.profileExtractionService = profileExtractionService;
        this.profileRepository = profileRepository;
        this.restClient = RestClient.builder().build();
    }

    // =========================================================
    // MAIN CHAT
    // =========================================================

    public String getReply(
            String conversationId,
            String userMessage
    ) {

        if (userMessage == null || userMessage.isBlank()) {
            return "Tell me what you'd like help with.";
        }

        String userId = conversationId;

        try {

            profileExtractionService.processUserMessage(
                    userId,
                    userMessage
            );

            List<Map<String, String>> messages =
                    buildMessages(
                            userId,
                            conversationId,
                            userMessage
                    );

            String reply =
                    callGroqWithRetry(messages);

            profileExtractionService.processAssistantReply(
                    userId,
                    reply
            );

            chatMemory.addMessage(
                    conversationId,
                    "user",
                    userMessage
            );

            chatMemory.addMessage(
                    conversationId,
                    "assistant",
                    reply
            );

            return reply;

        } catch (Exception e) {

            logger.error(
                    "Groq request failed: {}",
                    e.getMessage()
            );

            return """
                    Nutri is having trouble responding right now.
                    Please try again in a moment.
                    """.trim();
        }
    }

    // =========================================================
    // BUILD REQUEST MESSAGES
    // =========================================================

    private List<Map<String, String>> buildMessages(
            String userId,
            String conversationId,
            String userMessage
    ) {

        List<Map<String, String>> messages =
                new ArrayList<>();

        messages.add(
                Map.of(
                        "role", "system",
                        "content", getSystemPrompt()
                )
        );

        messages.add(
                Map.of(
                        "role", "system",
                        "content", buildProfileContext(userId)
                )
        );

        List<Map<String, String>> history =
                chatMemory.getHistory(conversationId);

        if (history.size() > HISTORY_LIMIT) {

            history =
                    history.subList(
                            history.size() - HISTORY_LIMIT,
                            history.size()
                    );
        }

        messages.addAll(history);

        messages.add(
                Map.of(
                        "role", "user",
                        "content", userMessage.trim()
                )
        );

        return messages;
    }

    // =========================================================
    // GROQ REQUEST
    // =========================================================

    private String callGroqWithRetry(
            List<Map<String, String>> messages
    ) {

        try {

            return callGroq(messages);

        } catch (HttpClientErrorException e) {

            if (e.getStatusCode()
                    != HttpStatus.TOO_MANY_REQUESTS) {

                throw e;
            }

            long waitMillis =
                    getRetryDelay(e);

            logger.warn(
                    "Groq rate limit reached. Retrying after {} ms.",
                    waitMillis
            );

            sleep(waitMillis);

            return callGroq(messages);
        }
    }

    @SuppressWarnings("rawtypes")
    private String callGroq(
            List<Map<String, String>> messages
    ) {

        Map<String, Object> requestBody =
                Map.of(
                        "model", model,
                        "messages", messages,
                        "temperature", 0.5,
                        "max_completion_tokens",
                        MAX_COMPLETION_TOKENS
                );

        Map response =
                restClient
                        .post()
                        .uri(apiUrl)
                        .header(
                                "Authorization",
                                "Bearer " + apiKey
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);

        return extractReply(response);
    }

    // =========================================================
    // RATE LIMIT
    // =========================================================

    private long getRetryDelay(
            HttpClientErrorException exception
    ) {

        if (exception.getResponseHeaders() != null) {

            String retryAfter =
                    exception
                            .getResponseHeaders()
                            .getFirst("Retry-After");

            if (retryAfter != null) {

                try {

                    double seconds =
                            Double.parseDouble(
                                    retryAfter.trim()
                            );

                    return Math.max(
                            1000L,
                            (long) (seconds * 1000)
                    );

                } catch (NumberFormatException ignored) {
                }
            }
        }

        return 2000L;
    }

    private void sleep(long milliseconds) {

        try {

            Thread.sleep(milliseconds);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Groq retry interrupted",
                    e
            );
        }
    }

    // =========================================================
    // PROFILE CONTEXT
    // =========================================================

    private String buildProfileContext(
            String userId
    ) {

        NutritionProfile profile =
                profileRepository
                        .findByUserId(userId)
                        .orElse(null);

        if (profile == null) {

            return """
                    USER PROFILE:
                    No profile information is known yet.

                    Learn useful information gradually.
                    Ask at most one relevant profile question.
                    """;
        }

        return """
                USER PROFILE:
                Goal: %s
                Diet: %s
                Age: %s
                Height cm: %s
                Weight kg: %s
                Gender: %s
                Activity: %s

                Use known information when personalizing responses.
                Never ask again for information already known.
                UNKNOWN values may be learned gradually.
                """
                .formatted(
                        value(profile.getGoal()),
                        value(profile.getDietType()),
                        value(profile.getAge()),
                        value(profile.getHeight()),
                        value(profile.getWeight()),
                        value(profile.getGender()),
                        value(profile.getActivityLevel())
                );
    }

    private String value(Object value) {

        return value == null
                ? "UNKNOWN"
                : value.toString();
    }

    // =========================================================
    // RESPONSE EXTRACTION
    // =========================================================

    private String extractReply(
            Map<?, ?> response
    ) {

        if (response == null) {
            return fallbackReply();
        }

        Object choicesObject =
                response.get("choices");

        if (!(choicesObject instanceof List<?> choices)
                || choices.isEmpty()) {

            return fallbackReply();
        }

        Object firstChoice =
                choices.get(0);

        if (!(firstChoice instanceof Map<?, ?> choice)) {
            return fallbackReply();
        }

        Object messageObject =
                choice.get("message");

        if (!(messageObject instanceof Map<?, ?> message)) {
            return fallbackReply();
        }

        Object content =
                message.get("content");

        if (content == null
                || content.toString().isBlank()) {

            return fallbackReply();
        }

        return content.toString().trim();
    }

    private String fallbackReply() {

        return """
                Sorry, I couldn't generate a response.
                Please try again.
                """.trim();
    }

    // =========================================================
    // NUTRI SYSTEM PROMPT
    // =========================================================

    private String getSystemPrompt() {

        return """
                You are Nutri, the AI nutrition companion in NutriVerse.

                STYLE
                - Be friendly, natural, concise and practical.
                - Usually reply in 2 to 4 short sentences.
                - Ask at most one question.
                - Do not behave like a questionnaire.
                - Avoid large tables unless explicitly requested.

                PROFILE
                - Use known USER PROFILE information.
                - Never ask again for information already known.
                - Learn missing information gradually when relevant.
                - Never guess age, height, weight, gender or activity level.
                - If activity information is vague, such as
                  "I run sometimes", ask approximately how many days per week.

                LOCAL FOOD AND BUDGET
                - Prefer affordable, familiar and locally available foods.
                - Adapt recommendations when the user mentions their city,
                  region, country, budget or student status.
                - For Indian users, prefer common Indian foods and staples
                  unless the user asks for something different.
                - Do not default to expensive or specialty foods such as
                  avocado, quinoa, berries, protein powder or imported foods.
                - If an ingredient may be expensive or difficult to find,
                  suggest a cheaper local alternative.

                DIET
                - Dietary restrictions are hard constraints.
                - VEGETARIAN: no meat, poultry or fish.
                - VEGAN: no meat, fish, eggs or dairy.
                - Respect foods the user explicitly avoids.

                ALLERGIES
                - Known allergies are hard safety constraints.
                - Never knowingly recommend a known allergen.
                - Suggest a safe alternative when necessary.

                NUTRITION FACTS
                - NEVER invent numerical nutrition values.
                - Do not state exact calories, protein grams, macros,
                  vitamins or minerals unless trusted backend nutrition
                  data supplied those values.
                - Without trusted numerical data, use qualitative wording
                  such as "protein-rich", "higher-protein option",
                  "fiber-rich" or "balanced".
                - Never claim a value came from USDA, Open Food Facts,
                  ICMR-NIN or another source unless that source was
                  actually supplied by the backend.

                PERSONALIZED TARGETS
                - General nutrition advice may be given with incomplete
                  profile information.
                - Exact calorie, BMR, TDEE or macro targets require the
                  necessary profile information.
                - Never guess missing values.

                EXPLAINABLE RECOMMENDATIONS
                - Every food or meal recommendation must be explainable.
                - Briefly explain WHY a recommendation fits the user.
                - Base explanations only on information actually known.

                Valid explanation factors include:
                - dietary preference
                - nutrition goal
                - activity level
                - budget
                - region or local availability
                - foods the user said they have or prefer
                - trusted nutrition information supplied by the backend

                - Do not invent explanation reasons.
                - Do not claim an allergy match unless that allergy is known.
                - Do not claim exact nutrition evidence unless trusted
                  backend data supplied it.

                For recommendations, prefer a short format such as:

                Recommendation:
                Moong dal chilla

                Why this fits you:
                ✓ Matches your dietary preference
                ✓ Supports your nutrition goal
                ✓ Uses affordable familiar ingredients

                Do not make the explanation long unless the user asks why.

                If the user asks:
                "Why did you recommend this?"
                "Why did you recommend oats?"
                or similar questions,

                explain using the known profile and recent conversation.
                Do not create new reasons that were not previously known.

                RECIPES
                - Respect diet, allergies, budget and familiar foods.
                - Keep recipes simple unless more detail is requested.
                - Do not invent precise recipe nutrition values.

                MEDICAL SAFETY
                - Do not diagnose diseases.
                - Do not prescribe or stop medication.
                - Do not encourage starvation or extreme dieting.
                - Do not guarantee weight loss or medical outcomes.
                - Refer complex clinical nutrition questions to an
                  appropriate healthcare professional.

                CORE RULE
                Help first.
                Learn gradually.
                Stay practical.
                Explain recommendations.
                Respect safety constraints.
                Never invent precise nutrition facts.
                """;
    }
}