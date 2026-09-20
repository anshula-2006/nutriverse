package com.nutriverse.backend;

import com.jayway.jsonpath.JsonPath;
import com.nutriverse.backend.config.JwtAuthenticationInterceptor;
import com.nutriverse.backend.controller.*;
import com.nutriverse.backend.dto.NutritionMealRequest;
import com.nutriverse.backend.dto.ProfileUpdateRequest;
import com.nutriverse.backend.model.User;
import com.nutriverse.backend.model.WaterLog;
import com.nutriverse.backend.repository.FoodNodeRepository;
import com.nutriverse.backend.repository.MealLogRepository;
import com.nutriverse.backend.repository.UserRepository;
import com.nutriverse.backend.service.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityRegressionTests {
    // Deliberately test-only material, never used by the running application.
    private static final String TEST_SECRET = "test-only-signing-material-012345678901234567890123456789";
    private final JwtService jwt = new JwtService(TEST_SECRET);
    private final DashboardService dashboard = mock(DashboardService.class);
    private final MealLogService meals = mock(MealLogService.class);
    private final WaterLogService water = mock(WaterLogService.class);
    private final NutritionProfileService profiles = mock(NutritionProfileService.class);
    private final NutritionLookupService lookup = mock(NutritionLookupService.class);
    private final NutritionMealService nutritionMeals = mock(NutritionMealService.class);
    private final GroqService groq = mock(GroqService.class);
    private final FoodNodeRepository graph = mock(FoodNodeRepository.class);
    private MockMvc mvc;
    private String authorization;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new DashboardController(dashboard), new MealLogController(meals),
                        new WaterLogController(water), new NutritionProfileController(profiles),
                        new NutritionController(lookup, nutritionMeals), new ChatController(groq, mock(RecommendationService.class)),
                        new KnowledgeGraphController(graph))
                .addInterceptors(new JwtAuthenticationInterceptor(jwt))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        authorization = "Bearer " + jwt.generateToken("owner", "test-user", "USER");
    }

    @Test
    void everyPrivateApiRejectsMissingAuthenticationBeforeCallingServices() throws Exception {
        for (String path : List.of("/api/dashboard", "/api/profile", "/api/meals", "/api/meals/today",
                "/api/water", "/api/water/today", "/api/water/today/total",
                "/api/nutrition/search?query=banana", "/api/nutrition/food?source=USDA&sourceId=123",
                "/api/nutrition/barcode/123", "/api/kg/foods")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String path : List.of("/api/chat", "/api/water", "/api/nutrition/log-meal")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(patch("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboard, meals, water, profiles, lookup, nutritionMeals, groq, graph);
    }

    @Test
    void identityComesFromJwtForReadsAndWritesDespiteClientSuppliedIds() throws Exception {
        for (String path : List.of("/api/dashboard", "/api/profile", "/api/meals/today", "/api/water/today")) {
            mvc.perform(get(path).param("userId", "victim").header("Authorization", authorization))
                    .andExpect(status().isOk());
        }
        verify(dashboard).getDashboard("owner");
        verify(profiles).getOrCreateProfile("owner");
        verify(meals).getTodayMeals("owner");
        verify(water).getTodayWaterLogs("owner");

        mvc.perform(post("/api/chat").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\",\"conversationId\":\"victim\",\"userId\":\"victim\"}"))
                .andExpect(status().isOk());
        verify(groq).getReply("owner", "Hello");

        mvc.perform(post("/api/water").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountLiters\":0.25,\"userId\":\"victim\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<WaterLog> waterEntry = ArgumentCaptor.forClass(WaterLog.class);
        verify(water).addWater(waterEntry.capture());
        assertEquals("owner", waterEntry.getValue().getUserId());
        assertEquals(0.25, waterEntry.getValue().getAmountLiters());

        mvc.perform(patch("/api/profile").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":25,\"userId\":\"victim\",\"dailyCalorieTarget\":99999}"))
                .andExpect(status().isOk());
        verify(profiles).updateProfile(eq("owner"), any(ProfileUpdateRequest.class));

        mvc.perform(post("/api/nutrition/log-meal").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealType\":\"LUNCH\",\"source\":\"USDA FoodData Central\",\"sourceId\":\"123\","
                                + "\"quantityGrams\":100,\"userId\":\"victim\",\"calories\":99999}"))
                .andExpect(status().isOk());
        verify(nutritionMeals).logMeal(eq("owner"), any(NutritionMealRequest.class));
    }

    @Test
    void selectedFoodChatValidatesThePairAndUsesTrustedIdentity() throws Exception {
        mvc.perform(post("/api/chat").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Protein?\",\"source\":\"USDA FoodData Central\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Protein?\",\"source\":\"USDA FoodData Central\",\"sourceId\":\"123\",\"userId\":\"victim\"}"))
                .andExpect(status().isOk());
        verify(groq).getReply("owner", "Protein?", "USDA FoodData Central", "123");
    }

    @Test
    void knowledgeGraphRequiresSignedAdminRole() throws Exception {
        mvc.perform(get("/api/kg/foods").header("Authorization", authorization).param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(graph);
        mvc.perform(get("/api/kg/foods").header("Authorization",
                        "Bearer " + jwt.generateToken("admin", "administrator", "ADMIN")))
                .andExpect(status().isOk());
        verify(graph).findAll();
    }

    @Test
    void malformedExpiredForgedAndIncompleteTokensAreRejected() throws Exception {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().claim("userId", "owner")
                .expiration(new Date(System.currentTimeMillis() - 60_000)).signWith(key).compact();
        String noExpiration = Jwts.builder().claim("userId", "owner").signWith(key).compact();
        String noIdentity = Jwts.builder().expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        String forged = new JwtService("different-test-only-signing-material-01234567890123456789")
                .generateToken("victim", "forged", "ADMIN");
        for (String token : List.of("not-a-jwt", expired, noExpiration, noIdentity, forged)) {
            mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/dashboard").header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboard);
    }

    @Test
    void invalidWaterAndProfileInputsNeverReachPersistence() throws Exception {
        for (String body : List.of("{}", "{\"amountLiters\":0}", "{\"amountLiters\":-1}",
                "{\"amountLiters\":11}")) {
            mvc.perform(post("/api/water").header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/profile").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"gender\":\"invalid\",\"age\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(water, profiles);
    }

    @Test
    void nutritionErrorsDistinguishMissingParametersAndMissingRecords() throws Exception {
        mvc.perform(get("/api/nutrition/search").header("Authorization", authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: query"));
        mvc.perform(get("/api/nutrition/food").param("source", "USDA")
                        .header("Authorization", authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: sourceId"));
        verifyNoInteractions(lookup);

        mvc.perform(get("/api/nutrition/barcode/123").header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No nutrition record was found for this barcode"));
        mvc.perform(get("/api/nutrition/food").param("source", "USDA").param("sourceId", "123")
                        .header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Selected food could not be retrieved from its source"));
    }

    @Test
    void missingSelectedFoodReturnsNotFoundWithoutSavingMeal() throws Exception {
        MealLogRepository repository = mock(MealLogRepository.class);
        MockMvc nutrition = MockMvcBuilders.standaloneSetup(new NutritionController(lookup,
                        new NutritionMealService(lookup, repository)))
                .addInterceptors(new JwtAuthenticationInterceptor(jwt))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        nutrition.perform(post("/api/nutrition/log-meal").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealType\":\"LUNCH\",\"source\":\"USDA FoodData Central\","
                                + "\"sourceId\":\"123\",\"quantityGrams\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Selected food could not be retrieved from its source. Please search again."));
        verifyNoInteractions(repository);
    }

    @Test
    void registrationHashesPasswordAndLoginIssuesUsableJwtWithoutExposingHash() throws Exception {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate database = mock(MongoTemplate.class);
        IndexOperations indexes = mock(IndexOperations.class);
        when(database.indexOps(User.class)).thenReturn(indexes);
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId("new-user");
            return saved;
        });
        MockMvc auth = MockMvcBuilders.standaloneSetup(new AuthController(users, jwt, database))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        auth.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"username\":\"test-user\",\"password\":\"Test-password-123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(users).save(savedUser.capture());
        User user = savedUser.getValue();
        assertNotEquals("Test-password-123", user.getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches("Test-password-123", user.getPasswordHash()));
        assertEquals("USER", user.getRole());
        when(users.findByUsername("test-user")).thenReturn(Optional.of(user));

        String response = auth.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.token");
        Claims claims = jwt.authenticate("Bearer " + token);
        assertEquals("new-user", claims.get("userId", String.class));
        assertEquals("USER", claims.get("role", String.class));
        auth.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentDuplicateSignupUsesUniqueIndexAndReturnsExistingUsernameError() throws Exception {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate database = mock(MongoTemplate.class);
        IndexOperations indexes = mock(IndexOperations.class);
        when(database.indexOps(User.class)).thenReturn(indexes);
        // Another request inserts the username after this request's existence check.
        when(users.save(any(User.class))).thenThrow(new DuplicateKeyException("internal database details"));
        MockMvc auth = MockMvcBuilders.standaloneSetup(new AuthController(users, jwt, database))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        auth.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"username\":\"test-user\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists"));
        var order = inOrder(indexes, users);
        ArgumentCaptor<IndexDefinition> index = ArgumentCaptor.forClass(IndexDefinition.class);
        order.verify(indexes).createIndex(index.capture());
        order.verify(users).existsByUsername("test-user");
        order.verify(users).save(any(User.class));
        assertEquals(1, index.getValue().getIndexKeys().get("username"));
        assertEquals(Boolean.TRUE, index.getValue().getIndexOptions().get("unique"));
    }

    @Test
    void legacyDuplicatesPreventRegistrationWithoutExposingDatabaseDetails() throws Exception {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate database = mock(MongoTemplate.class);
        IndexOperations indexes = mock(IndexOperations.class);
        when(database.indexOps(User.class)).thenReturn(indexes);
        when(indexes.createIndex(any(IndexDefinition.class)))
                .thenThrow(new DuplicateKeyException("legacy duplicate username with internal details"));
        MockMvc auth = MockMvcBuilders.standaloneSetup(new AuthController(users, jwt, database))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        auth.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"username\":\"test-user\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Data service unavailable. Please try again shortly."));
        verifyNoInteractions(users);
        verify(indexes, never()).dropAllIndexes();
        verify(indexes, never()).dropIndex(anyString());
    }
}
