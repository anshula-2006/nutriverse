package com.nutriverse.backend.service;

import com.nutriverse.backend.controller.ChatController;
import com.nutriverse.backend.dto.ChatRequest;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.dto.RecommendationResponse;
import com.nutriverse.backend.model.ChatMessage;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.repository.ChatMessageRepository;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationConversationTests {
    private static final String REQUEST = "Suggest me a high protein meal";
    private static final String USDA = "USDA FoodData Central";
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final NutritionProfileRepository profiles = mock(NutritionProfileRepository.class);
    private final ProfileExtractionService extraction = mock(ProfileExtractionService.class);
    private final NutritionLookupService lookup = mock(NutritionLookupService.class);
    private final GroqService candidates = mock(GroqService.class);
    private final List<ChatMessage> stored = new ArrayList<>();
    private final Map<String, NutritionResult> exactRecords = new LinkedHashMap<>();
    private final NutritionProfile profile = new NutritionProfile("owner");
    private ChatMemory memory;

    @BeforeEach
    void persistMessagesAndSupplyVerifiedFixtures() {
        when(messages.save(any(ChatMessage.class))).thenAnswer(call -> {
            ChatMessage message = call.getArgument(0);
            message.setTimestamp(Instant.EPOCH.plusMillis(stored.size()));
            stored.add(message);
            return message;
        });
        when(messages.findTop10ByConversationIdOrderByTimestampDesc(anyString())).thenAnswer(call ->
                stored.stream()
                        .filter(message -> call.getArgument(0).equals(message.getConversationId()))
                        .sorted(Comparator.comparing(ChatMessage::getTimestamp).reversed())
                        .limit(10).toList());
        memory = new ChatMemory(messages);
        profile.setDietType("VEGETARIAN");
        profile.setGoal("WEIGHT_LOSS");
        profile.setDietaryRestriction("GLUTEN_FREE");
        profile.setFoodPreferences("lentils, tofu");
        profile.setFoodDislikes("peanuts");
        profile.setAge(30);
        profile.setHeight(170.0);
        profile.setWeight(70.0);
        profile.setGender("FEMALE");
        profile.setActivityLevel("MODERATE");
        when(profiles.findByUserId("owner")).thenReturn(java.util.Optional.of(profile));

        List<String> names = List.of("chickpeas", "lentils", "tofu", "tempeh", "edamame",
                "black beans", "kidney beans", "quinoa", "amaranth");
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(names);
        for (int i = 0; i < names.size(); i++) {
            String id = Integer.toString(100 + i);
            // Distinct search and detail values catch accidental use of search summaries as exact facts.
            when(lookup.search(names.get(i))).thenReturn(List.of(food(id, names.get(i), 20.0 - i)));
            exactRecords.put(id, food(id, names.get(i), 8.25 + i));
        }
        when(lookup.findBySourceId(eq(USDA), anyString()))
                .thenAnswer(call -> exactRecords.get(call.getArgument(1)));
    }

    @Test
    void repeatedFollowupsKeepIntentProfileAndPersistedExclusionsAcrossServiceRestart() {
        RecommendationResponse first = service().recommend("owner", REQUEST);
        memory = new ChatMemory(messages);
        RecommendationResponse second = service().recommend("owner", "Any other suggestions?");
        RecommendationResponse third = service().recommend("owner", "Something else?");

        for (RecommendationResponse response : List.of(first, second, third)) {
            assertEquals("EVIDENCE_STRUCTURED", response.getExplanationMethod());
            assertEquals("VEGETARIAN", response.getInterpretedDiet());
            assertEquals("WEIGHT_LOSS", response.getInterpretedGoal());
            assertEquals(3, response.getRecommendations().size());
            for (var item : response.getRecommendations()) {
                assertTrue(item.getReason().contains("protein-focused filter"));
                assertTrue(item.getWhy().stream().anyMatch(reason -> reason.contains("vegetarian")));
                assertTrue(item.getWhy().stream().anyMatch(reason -> reason.contains("weight loss")));
                assertTrue(item.getWhy().stream().anyMatch(reason -> reason.contains("gluten")));
                assertTrue(item.getEvidence().isVerified());
                assertEquals(USDA, item.getEvidence().getSource());
                assertEquals(exactRecords.get(item.getEvidence().getSourceId()).getProtein(),
                        item.getEvidence().getProtein());
            }
        }
        assertEquals(9, Stream.of(first, second, third).flatMap(response -> ids(response).stream()).distinct().count());
        verify(candidates, times(3)).generateFoodCandidates(eq("owner"),
                contains("high protein"), anyCollection(), anyInt());
        verify(candidates, atLeastOnce()).generateFoodCandidates(eq("owner"), contains("high protein"),
                argThat(excluded -> first.getRecommendations().stream()
                        .allMatch(item -> excluded.contains(item.getWhat()))), anyInt());
        assertEquals(List.of(REQUEST, "Any other suggestions?", "Something else?"), memory.getHistory("owner")
                .stream().filter(message -> "user".equals(message.get("role")))
                .map(message -> message.get("content")).toList());
        assertEquals(3, memory.getRecentRecommendations("owner").size());
        assertEquals(List.of("user", "assistant", "user", "assistant", "user", "assistant"),
                memory.getHistory("owner").stream().map(message -> message.get("role")).toList());
        verify(profiles, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"any other suggestions?", "more suggestions", "more options", "something else",
            "anything else", "another", "another option", "another suggestion",
            "different options", "different recommendations", "give me more",
            "show me a few more", "what else can I have?", "can I get more?"})
    void alternativePhrasesReuseThePreviousRequest(String followup) {
        RecommendationResponse first = service().recommend("owner", REQUEST);
        RecommendationResponse next = service().recommend("owner", followup);
        assertEquals(3, next.getRecommendations().size());
        assertTrue(Collections.disjoint(ids(first), ids(next)));
        assertTrue(next.getRecommendations().stream()
                .allMatch(item -> item.getReason().contains("protein-focused filter")));
        assertTrue(service().isStructuredFollowup("owner", followup));
    }

    @Test
    void unrelatedConversationStopsStructuredFollowupRouting() {
        service().recommend("owner", REQUEST);
        memory.addMessage("owner", "user", "What is fiber?");
        memory.addMessage("owner", "assistant", "Fiber is a type of carbohydrate.");

        assertFalse(service().isStructuredFollowup("owner", "Anything else?"));
    }

    @Test
    void deletingHistoryUsesOnlyTheAuthenticatedConversationId() {
        when(messages.deleteByConversationId("owner")).thenReturn(6L);

        assertEquals(6L, service().clearChatHistory("owner"));

        verify(messages).deleteByConversationId("owner");
        verify(messages, never()).deleteByConversationId("someone-else");
    }

    @Test
    void lowProteinFoodsDoNotPassTheProteinFocusedFilter() {
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(List.of("idli", "tofu"));
        when(lookup.search("idli")).thenReturn(List.of(food("900", "idli", 6.36)));
        when(lookup.search("tofu")).thenReturn(List.of(food("901", "tofu", 12.0)));
        exactRecords.put("900", food("900", "idli", 6.36));
        exactRecords.put("901", food("901", "tofu", 12.0));

        RecommendationResponse response = service().recommend("owner", REQUEST);

        assertEquals(Set.of("901"), ids(response));
        assertTrue(response.getRecommendations().getFirst().getReason()
                .contains("at least 8 g protein per 100 g"));
    }

    @Test
    void exhaustedCandidatesReturnNoResultsInsteadOfRepeatingPreviousFoods() {
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(List.of("chickpeas", "lentils", "tofu"));
        assertEquals(3, service().recommend("owner", REQUEST).getRecommendations().size());
        assertTrue(service().recommend("owner", "another option").getRecommendations().isEmpty());
        assertTrue(service().recommend("owner", "something else").getRecommendations().isEmpty());
    }

    @Test
    void aliasesCannotRepeatAnAlreadyShownSourceRecord() {
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(List.of("lentils"));
        assertEquals(Set.of("101"), ids(service().recommend("owner", REQUEST)));
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(List.of("masoor dal", "tofu"));
        when(lookup.search("masoor dal")).thenReturn(List.of(food("101", "masoor dal", 40.0)));
        exactRecords.put("101", food("101", "masoor dal", 40.0));
        assertEquals(Set.of("102"), ids(service().recommend("owner", "another")));
    }

    @Test
    void chatEndpointRoutesAlternativesThroughEvidenceAndPreservesSelectedSourceQueries() {
        RecommendationResponse first = service().recommend("owner", REQUEST);
        ChatController controller = new ChatController(candidates, service());
        ChatRequest request = new ChatRequest();
        request.setMessage("Any other suggestions?");
        var response = controller.chat("owner", request);
        assertNotNull(response.getRecommendation());
        assertEquals(3, response.getRecommendation().getRecommendations().size());
        assertTrue(Collections.disjoint(ids(first), ids(response.getRecommendation())));
        verify(candidates, never()).getReply(anyString(), anyString());

        request.setMessage("How much protein does this contain?");
        request.setSource(USDA);
        request.setSourceId("100");
        when(candidates.getReply("owner", request.getMessage(), USDA, "100")).thenReturn("Exact source facts");
        var selectedResponse = controller.chat("owner", request);
        assertEquals("Exact source facts", selectedResponse.getReply());
        assertNull(selectedResponse.getRecommendation());
        verify(candidates).getReply("owner", request.getMessage(), USDA, "100");
    }

    @Test
    void anotherUsersConversationCannotSupplyIntentOrExcludeTheirFoods() {
        RecommendationResponse first = service().recommend("owner", REQUEST);
        assertTrue(memory.getHistory("someone-else").isEmpty());
        assertTrue(memory.getRecentRecommendations("someone-else").isEmpty());
        assertFalse(service().isStructuredFollowup("someone-else", "another option"));
        when(profiles.findByUserId("someone-else")).thenReturn(java.util.Optional.of(profile));
        RecommendationResponse other = service().recommend("someone-else", REQUEST);
        assertEquals(ids(first), ids(other));
        assertEquals(2, memory.getHistory("owner").size());
        assertEquals(2, memory.getHistory("someone-else").size());
    }

    @Test
    void chronologicalMemoryIgnoresBlankMessagesAndInvalidConversationIds() {
        memory.addMessage("owner", "user", " first ");
        memory.addMessage("owner", "assistant", " second ");
        memory.addMessage("someone-else", "user", "private message");
        memory.addMessage("owner", "user", " ");
        memory.addMessage("owner", "assistant", null);
        memory.addMessage(null, "user", "ignored");
        memory.addMessage(" ", "assistant", "ignored");
        assertEquals(List.of(Map.of("role", "user", "content", "first"),
                Map.of("role", "assistant", "content", "second")), memory.getHistory("owner"));
        assertTrue(memory.getHistory(null).isEmpty());
        assertTrue(memory.getHistory(" ").isEmpty());
        assertEquals(3, stored.size());
    }

    @ParameterizedTest
    @MethodSource("unsafeRecords")
    void hardConstraintsFilterActualSearchRecords(String diet, String restriction, String dislikes, String name) {
        setConstraints(diet, restriction, dislikes);
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(List.of("lentils"));
        when(lookup.search("lentils")).thenReturn(List.of(food("900", name, 30.0), food("901", "lentils plain", 10.0)));
        exactRecords.put("900", food("900", name, 30.0));
        exactRecords.put("901", food("901", "lentils plain", 10.0));
        RecommendationResponse result = service().recommend("owner", REQUEST);
        assertEquals(Set.of("901"), ids(result));
        verify(lookup, never()).findBySourceId(USDA, "900");
    }

    @ParameterizedTest
    @MethodSource("unsafeRecords")
    void hardConstraintsAreRecheckedAgainstTheExactRecord(String diet, String restriction, String dislikes, String name) {
        setConstraints(diet, restriction, dislikes);
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(List.of("lentils"));
        exactRecords.put("101", food("101", name, 30.0));
        assertTrue(service().recommend("owner", REQUEST).getRecommendations().isEmpty());
        verify(lookup).findBySourceId(USDA, "101");
    }

    @Test
    void namedNutritionFollowupRefetchesSavedSourceAndWhyUsesPersistedReasons() {
        RecommendationResponse first = service().recommend("owner", REQUEST);
        RecommendationResponse latest = service().recommend("owner", "something else");
        var selected = first.getRecommendations().getFirst();
        String id = selected.getEvidence().getSourceId();
        exactRecords.get(id).setProtein(18.375);
        exactRecords.get(id).setFiber(null);
        clearInvocations(lookup);
        GroqService chat = new GroqService(new ChatMemory(messages), extraction, profiles, lookup);
        String answer = chat.getReply("owner", "How much protein does " + selected.getWhat() + " have?");
        assertTrue(answer.contains("18.375"), answer);
        assertTrue(answer.contains(id), answer);
        assertTrue(answer.contains("not available"), answer);
        verify(lookup).findBySourceId(USDA, id);

        String why = chat.getReply("owner", "Why did you recommend it?");
        assertTrue(latest.getRecommendations().stream().anyMatch(item ->
                why.contains(item.getReason()) && item.getWhy().stream().allMatch(why::contains)), why);
        verifyNoMoreInteractions(lookup);
        assertEquals(8, memory.getHistory("owner").size());
    }

    @Test
    void exactFoodNamesTakePriorityOverSharedWordsButAmbiguousNamesNeverGuess() {
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(List.of("black beans", "kidney beans"));
        assertEquals(Set.of("105", "106"), ids(service().recommend("owner", REQUEST)));
        clearInvocations(lookup);
        GroqService chat = new GroqService(memory, extraction, profiles, lookup);
        String answer = chat.getReply("owner", "How much protein does black beans have?");
        assertTrue(answer.contains("Source ID: 105"), answer);
        assertTrue(answer.contains("Protein: 13.25 g"), answer);
        verify(lookup).findBySourceId(USDA, "105");
        String ambiguous = chat.getReply("owner", "How much protein do beans have?");
        assertTrue(ambiguous.contains("don't have verified nutrition values"), ambiguous);
        verifyNoMoreInteractions(lookup);
    }

    @Test
    void NutrientWordsCannotSelectAnUnrelatedRecommendedFood() {
        when(candidates.generateFoodCandidates(anyString(), anyString(), anyCollection(), anyInt()))
                .thenReturn(List.of("soy protein isolate"));
        when(lookup.search("soy protein isolate")).thenReturn(List.of(food("950", "soy protein isolate", 40.0)));
        exactRecords.put("950", food("950", "soy protein isolate", 40.0));
        assertEquals(Set.of("950"), ids(service().recommend("owner", REQUEST)));
        clearInvocations(lookup);
        GroqService chat = new GroqService(memory, extraction, profiles, lookup);
        String answer = chat.getReply("owner", "How much protein does chicken have?");
        assertTrue(answer.contains("don't have verified nutrition values"), answer);
        verifyNoInteractions(lookup);
    }

    @Test
    void recipeRequestsAndTheirAlternativesStayInTheRecipeConversation() {
        service().recommend("owner", REQUEST);
        ChatController controller = new ChatController(candidates, service());
        ChatRequest request = new ChatRequest();
        request.setMessage("Any other recipes?");
        when(candidates.getReply("owner", request.getMessage())).thenReturn("A compatible recipe");
        assertFalse(service().isStructuredFollowup("owner", request.getMessage()));
        assertNull(controller.chat("owner", request).getRecommendation());
        verify(candidates).getReply("owner", request.getMessage());

        memory.addMessage("owner", "user", "How do I cook tofu?");
        memory.addMessage("owner", "assistant", "Recommendation: Tofu skillet\nWhy this fits you: You asked for cooking help.");
        request.setMessage("Something else?");
        when(candidates.getReply("owner", request.getMessage())).thenReturn("Another compatible recipe");
        assertFalse(service().isStructuredFollowup("owner", request.getMessage()));
        assertNull(controller.chat("owner", request).getRecommendation());
        verify(candidates).getReply("owner", request.getMessage());
    }

    @Test
    void explicitExclusionsSurviveInitialRequestAndLaterConstraintBearingFollowups() {
        RecommendationResponse first = service().recommend("owner", REQUEST + " without tofu");
        assertEquals(3, first.getRecommendations().size());
        assertFalse(ids(first).contains("102"));
        RecommendationResponse next = service().recommend("owner", "another option without edamame");
        assertEquals(3, next.getRecommendations().size());
        assertTrue(Collections.disjoint(Set.of("102", "104"), ids(next)));
        clearInvocations(candidates);
        memory = new ChatMemory(messages);
        RecommendationResponse third = service().recommend("owner", "something else");
        assertEquals(Set.of("108"), ids(third));
        verify(candidates).generateFoodCandidates(eq("owner"), contains("high protein"),
                argThat(excluded -> excluded.contains("tofu") && excluded.contains("edamame")), anyInt());
    }

    @Test
    void namedWhyUsesTheOriginalFoodInsteadOfTheMostRecentBatch() {
        RecommendationResponse first = service().recommend("owner", REQUEST);
        RecommendationResponse latest = service().recommend("owner", "another option");
        var selected = first.getRecommendations().getFirst();
        GroqService chat = new GroqService(memory, extraction, profiles, lookup);
        String why = chat.getReply("owner", "Why did you recommend " + selected.getWhat() + "?");
        assertTrue(why.contains("Recommendation: " + selected.getWhat()), why);
        assertTrue(why.contains(selected.getReason()), why);
        assertTrue(selected.getWhy().stream().allMatch(why::contains), why);
        assertFalse(why.contains("Recommendation: " + latest.getRecommendations().getFirst().getWhat()), why);
    }

    @Test
    void whyAfterARecipeUsesTheNewRecipeInsteadOfOlderStructuredCards() {
        service().recommend("owner", REQUEST);
        String recipe = "Recommendation: Tofu skillet\nWhy this fits you: You asked for a quick recipe.";
        memory.addMessage("owner", "user", "Suggest a quick recipe");
        memory.addMessage("owner", "assistant", recipe);
        GroqService chat = new GroqService(memory, extraction, profiles, lookup);
        String why = chat.getReply("owner", "Why did you recommend it?");
        assertTrue(why.contains(recipe), why);
        assertFalse(why.contains("Recommendation: chickpeas"), why);
    }

    private RecommendationService service() {
        return new RecommendationService(profiles, lookup, extraction, candidates, memory);
    }

    private void setConstraints(String diet, String restriction, String dislikes) {
        profile.setDietType(diet);
        profile.setDietaryRestriction(restriction);
        profile.setFoodDislikes(dislikes);
    }

    private static Stream<Arguments> unsafeRecords() {
        Stream<Arguments> dietAndDislikes = Stream.of(
                Arguments.of("VEGETARIAN", null, null, "lentils with chicken"),
                Arguments.of("VEGETARIAN", null, null, "lentils with shrimp"),
                Arguments.of("VEGETARIAN", null, null, "lentils with fish"),
                Arguments.of("VEGAN", null, null, "lentils with milk"),
                Arguments.of("VEGAN", null, null, "lentils with eggs"),
                Arguments.of("VEGAN", null, null, "lentils with cheese"),
                Arguments.of("VEGETARIAN", null, "peanuts", "lentils with peanuts"));
        Stream<Arguments> gluten = Stream.of("wheat", "barley", "rye", "malt", "semolina", "bulgur",
                        "couscous", "seitan", "spelt", "farro", "triticale", "oats")
                .map(grain -> Arguments.of("VEGETARIAN", "GLUTEN_FREE", null, "lentils with " + grain));
        return Stream.concat(dietAndDislikes, gluten);
    }

    private Set<String> ids(RecommendationResponse response) {
        return response.getRecommendations().stream()
                .map(item -> item.getEvidence().getSourceId()).collect(Collectors.toSet());
    }

    private NutritionResult food(String id, String name, double protein) {
        NutritionResult food = new NutritionResult();
        food.setSource(USDA);
        food.setSourceId(id);
        food.setSourceType("AUTHORITATIVE_DATABASE");
        food.setDataType("Foundation");
        food.setVerified(true);
        food.setFoodName(name);
        food.setServingSize(100.0);
        food.setServingUnit("g");
        food.setProtein(protein);
        food.setCalories(100.0);
        food.setFiber(5.0);
        return food;
    }
}
