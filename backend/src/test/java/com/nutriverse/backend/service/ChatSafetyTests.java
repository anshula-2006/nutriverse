package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ChatSafetyTests {
    private final ChatMemory memory = mock(ChatMemory.class);
    private final NutritionProfileRepository profiles = mock(NutritionProfileRepository.class);
    private final ProfileExtractionService extraction = mock(ProfileExtractionService.class);
    private final NutritionLookupService lookup = mock(NutritionLookupService.class);
    private final GroqService service = new GroqService(memory, extraction, profiles, lookup);

    @Test
    void quantitativeQuestionWithoutRetrievedFactsNeverCallsTheModel() {
        when(memory.getHistory("owner")).thenReturn(List.of());
        String answer = service.getReply("owner", "How much protein does this food contain?");
        assertTrue(answer.contains("don't have verified nutrition values"));
        assertFalse(answer.matches(".*\\d.*"));
        verifyNoInteractions(lookup);
        verify(memory).addMessage("owner", "assistant", answer);
    }

    @Test
    void exactSelectedSourceSuppliesValuesAndUnknownsWithoutModelGeneration() {
        when(memory.getHistory("owner")).thenReturn(List.of());
        NutritionResult food = new NutritionResult();
        food.setSource("USDA FoodData Central");
        food.setSourceId("123");
        food.setSourceType("AUTHORITATIVE_DATABASE");
        food.setVerified(true);
        food.setFoodName("Synthetic test food");
        food.setServingSize(100.0);
        food.setServingUnit("g");
        food.setProtein(2.75);
        when(lookup.findBySourceId("USDA FoodData Central", "123")).thenReturn(food);
        String answer = service.getReply("owner", "How much protein does this food contain?", "USDA FoodData Central", "123");
        assertTrue(answer.contains("Protein: 2.75 g"));
        assertTrue(answer.contains("Calories: not available"));
        assertTrue(answer.contains("Source ID: 123"));
        food.setVerified(false);
        assertTrue(service.getReply("owner", "How much protein?", "USDA FoodData Central", "123")
                .startsWith("Source not verified."));
    }

    @Test
    void qualitativeOutputGuardWithholdsNumbersAndUnsupportedSourceClaims() {
        for (String reply : List.of("Contains 18g protein", "Contains eighteen grams of protein",
                "Contains half a gram of iron", "Contains ١٨ grams of protein", "Verified by USDA")) {
            String guarded = ReflectionTestUtils.invokeMethod(service, "guardQualitativeReply", reply);
            assertTrue(guarded.contains("don't have verified nutrition values"), reply);
        }
        assertEquals("Beans can be a protein-containing option.",
                ReflectionTestUtils.invokeMethod(service, "guardQualitativeReply", "Beans can be a protein-containing option."));
        String numberedIdeas = "1. Try baked potato chips.\n2. Try roasted makhana.\nBake until crisp.";
        assertEquals(numberedIdeas,
                ReflectionTestUtils.invokeMethod(service, "guardQualitativeReply", numberedIdeas));
    }

    @Test
    void naturalConfirmationContinuesTheLatestAssistantOffer() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "I feel like eating something unhealthy"),
                Map.of("role", "assistant", "content",
                        "Tell me which treat you are craving and I can suggest an alternative.")
        );

        String resolved = ReflectionTestUtils.invokeMethod(
                service, "resolveFollowup", history, "Yes, I'd like that");

        assertNotNull(resolved);
        assertTrue(resolved.contains("accepted your most recent offer"));
    }

    @Test
    void oneWordSnackAnswerKeepsSafeQualitativeSuggestions() {
        when(memory.getHistory("owner")).thenReturn(List.of(
                Map.of("role", "assistant", "content", "What kind of snack are you thinking of?")
        ));
        MockRestServiceServer server = mockGroq();
        server.expect(requestTo("https://groq.example/chat"))
                .andExpect(content().string(containsString("chips")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"1. Try baked potato chips.\\n2. Try roasted makhana."}}]}
                        """, MediaType.APPLICATION_JSON));

        String answer = service.getReply("owner", "chips");

        assertTrue(answer.contains("baked potato chips"));
        assertFalse(answer.contains("don't have verified nutrition values"));
        verify(memory).addMessage("owner", "user", "chips");
        verify(memory).addMessage("owner", "assistant", answer);
        server.verify();
    }

    @Test
    void whyReusesSavedReasonsRatherThanGeneratingNewReasons() {
        String previous = "Recommendation: Lentil soup\nWhy this fits you: You told me you are vegetarian.";
        when(memory.getHistory("owner")).thenReturn(List.of(Map.of("role", "assistant", "content", previous)));
        String answer = service.getReply("owner", "Why did you recommend this?");
        assertTrue(answer.endsWith(previous));
        verifyNoInteractions(lookup);
    }

    @Test
    void providerRetryDelayCannotSleepIndefinitely() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "99999999999999999999999999");
        var error = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", headers,
                new byte[0], StandardCharsets.UTF_8);
        Long delay = ReflectionTestUtils.invokeMethod(service, "getRetryDelay", error);
        assertTrue(delay >= 1000 && delay <= 5000);
    }

    @Test
    void candidateRateLimitRetriesOnceThenReturnsSanitized429() {
        MockRestServiceServer server = mockGroq();
        server.expect(ExpectedCount.times(2), requestTo("https://groq.example/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "0").body("private provider details"));

        ResponseStatusException error = assertTimeout(Duration.ofSeconds(8), () ->
                assertThrows(ResponseStatusException.class,
                        () -> service.generateFoodCandidates("owner", "high protein meal", List.of(), 6)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals("Nutri is busy. Please try again shortly.", error.getReason());
        server.verify();
    }

    @Test
    void candidateHttpAndTransportFailuresReturnSanitized503() {
        MockRestServiceServer server = mockGroq();
        server.expect(requestTo("https://groq.example/chat"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("private provider details"));
        ResponseStatusException unavailable = assertThrows(ResponseStatusException.class,
                () -> service.generateFoodCandidates("owner", "high protein meal", List.of(), 6));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("Nutri is temporarily unavailable.", unavailable.getReason());
        server.verify();

        server.reset();
        server.expect(requestTo("https://groq.example/chat"))
                .andRespond(withException(new IOException("private connection details")));
        ResponseStatusException offline = assertThrows(ResponseStatusException.class,
                () -> service.generateFoodCandidates("owner", "high protein meal", List.of(), 6));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, offline.getStatusCode());
        assertEquals("Nutri is temporarily unavailable.", offline.getReason());
        server.verify();
    }

    @Test
    void malformedCandidateResponsesReturn502RatherThanFoodNames() {
        for (String response : List.of("{}", "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{\"content\":\"\"}}]}", "not-json")) {
            MockRestServiceServer server = mockGroq();
            server.expect(requestTo("https://groq.example/chat"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.generateFoodCandidates("owner", "high protein meal", List.of(), 6));
            assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode(), response);
            assertEquals("Nutri returned an invalid response. Please try again.", error.getReason());
            server.verify();
        }
    }

    @Test
    void candidatePromptUsesSavedProfileAndFiltersModelNutritionNumbers() {
        NutritionProfile profile = new NutritionProfile("owner");
        profile.setAge(37);
        profile.setHeight(168.0);
        profile.setWeight(64.0);
        profile.setGender("FEMALE");
        profile.setActivityLevel("MODERATE");
        profile.setDietType("VEGAN");
        profile.setDietaryRestriction("GLUTEN_FREE");
        profile.setGoal("WEIGHT_LOSS");
        profile.setFoodPreferences("beans");
        profile.setFoodDislikes("mushrooms");
        when(profiles.findByUserId("owner")).thenReturn(Optional.of(profile));
        MockRestServiceServer server = mockGroq();
        server.expect(requestTo("https://groq.example/chat"))
                .andExpect(content().string(containsString("Age: 37")))
                .andExpect(content().string(containsString("Height cm: 168.0")))
                .andExpect(content().string(containsString("Weight kg: 64.0")))
                .andExpect(content().string(containsString("Gender: FEMALE")))
                .andExpect(content().string(containsString("Activity: MODERATE")))
                .andExpect(content().string(containsString("Diet: VEGAN")))
                .andExpect(content().string(containsString("Dietary restriction: GLUTEN_FREE")))
                .andExpect(content().string(containsString("Goal: WEIGHT_LOSS")))
                .andExpect(content().string(containsString("Food preferences: beans")))
                .andExpect(content().string(containsString("Food dislikes: mushrooms")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"Lentils\\nChickpeas\\nTofu 18g protein\\nRice contains eighteen grams of protein\\nProtein: 18 g"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(List.of("Lentils", "Chickpeas"),
                service.generateFoodCandidates("owner", "high protein meal", List.of("Tofu"), 6));
        verifyNoInteractions(lookup, memory, extraction);
        server.verify();
    }

    private MockRestServiceServer mockGroq() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(service, "restClient", builder.build());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://groq.example/chat");
        ReflectionTestUtils.setField(service, "model", "test-model");
        return server;
    }

    @Test
    void ordinaryFoodQuestionsAndWrongOnboardingUnitsDoNotAlterProfiles() {
        var targets = mock(DailyTargetService.class);
        var realExtraction = new ProfileExtractionService(profiles, targets);
        when(profiles.findByUserId("owner")).thenReturn(Optional.of(new NutritionProfile("owner")));
        for (String message : List.of("How many grams of protein does rice contain?", "Give me another option",
                "I eat rice every day", "My friend weighs 70 kg")) {
            realExtraction.processUserMessage("owner", message);
        }
        realExtraction.processAssistantReply("owner", "What is your age?");
        realExtraction.processUserMessage("owner", "30g protein");
        realExtraction.processAssistantReply("owner", "What is your age?");
        realExtraction.processUserMessage("owner", "999999999999999999999999999999");
        verify(profiles, never()).save(any());
        verifyNoInteractions(targets);
    }

    @Test
    void explicitMeasurementsAreSavedWithoutInventingAge() {
        var targets = mock(DailyTargetService.class);
        var realExtraction = new ProfileExtractionService(profiles, targets);
        var profile = new NutritionProfile("owner");
        when(profiles.findByUserId("owner")).thenReturn(Optional.of(profile));
        realExtraction.processUserMessage("owner", "I am 70 kg");
        assertEquals(70.0, profile.getWeight());
        assertNull(profile.getAge());
        realExtraction.processUserMessage("owner", "I am 25 years old");
        realExtraction.processUserMessage("owner", "I am vegan");
        assertEquals(25, profile.getAge());
        assertEquals("VEGAN", profile.getDietType());
        verify(targets, times(3)).calculateTargets("owner");
    }
}
