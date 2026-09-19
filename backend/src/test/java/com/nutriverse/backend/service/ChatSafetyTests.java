package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
