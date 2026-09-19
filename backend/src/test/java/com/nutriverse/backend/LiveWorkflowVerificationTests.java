package com.nutriverse.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MongoDB and provider smoke test; only a newly generated audit database is touched. */
@EnabledIfEnvironmentVariable(named = "NUTRIVERSE_LIVE_WORKFLOW", matches = "true")
@SpringBootTest(properties = {
        "JWT_SECRET=isolated-live-test-key-never-used-by-production",
        "logging.level.root=WARN", "logging.level.org.springframework.web=INFO", "debug=false"
})
class LiveWorkflowVerificationTests {
    private static final String DATABASE = "nutriverse_audit_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.mongodb.database", () -> DATABASE);
    }
    @Autowired WebApplicationContext context;
    @Autowired MongoTemplate mongo;

    @Test
    void registerAuthenticatePersistRetrieveAndExplainUsingRealServices() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        assertEquals(DATABASE, mongo.getDb().getName());
        try {
            String credentials = "{\"username\":\"audit_user\",\"password\":\"Synthetic-audit-password-2026\"}";
            mvc.perform(post("/api/auth/register").contentType("application/json")
                            .content("{\"name\":\"Synthetic Audit User\",\"username\":\"audit_user\",\"password\":\"Synthetic-audit-password-2026\"}"))
                    .andExpect(status().isOk());
            String login = mvc.perform(post("/api/auth/login").contentType("application/json").content(credentials))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String token = JsonPath.read(login, "$.token");
            String authorization = "Bearer " + token;
            System.out.println("LIVE WORKFLOW: registration and login PASS");
            mvc.perform(get("/api/profile").header("Authorization", authorization)).andExpect(status().isOk());
            mvc.perform(patch("/api/profile").header("Authorization", authorization).contentType("application/json")
                            .content("{\"age\":30,\"height\":175,\"weight\":70,\"gender\":\"MALE\",\"dietType\":\"VEGETARIAN\",\"activityLevel\":\"MODERATELY_ACTIVE\",\"goal\":\"MAINTENANCE\",\"dailyCalorieTarget\":99999}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.dailyCalorieTarget").value(org.hamcrest.Matchers.not(99999)));
            mvc.perform(post("/api/water").header("Authorization", authorization).contentType("application/json")
                            .content("{\"amountLiters\":0.35,\"userId\":\"somebody-else\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/water/today/total").header("Authorization", authorization))
                    .andExpect(status().isOk()).andExpect(content().string("0.35"));
            mvc.perform(get("/api/dashboard").header("Authorization", authorization))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.waterConsumed").value(0.35));
            System.out.println("LIVE WORKFLOW: profile, server targets, water, dashboard persistence PASS");
            mvc.perform(post("/api/chat").header("Authorization", authorization).contentType("application/json")
                            .content("{\"message\":\"How much protein does this food contain?\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("don't have verified nutrition values")));
            String search = mvc.perform(get("/api/nutrition/search").param("query", "banana")
                            .header("Authorization", authorization))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> foods = JsonPath.read(search, "$");
            assertFalse(foods.isEmpty());
            Map<String, Object> food = foods.getFirst();
            assertEquals("USDA FoodData Central", food.get("source"));
            String sourceId = (String) food.get("sourceId");
            mvc.perform(get("/api/nutrition/food").param("source", "USDA FoodData Central").param("sourceId", sourceId)
                            .header("Authorization", authorization))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.sourceId").value(sourceId))
                    .andExpect(jsonPath("$.verified").value(true));
            mvc.perform(post("/api/nutrition/log-meal").header("Authorization", authorization).contentType("application/json")
                            .content("{\"mealType\":\"SNACK\",\"source\":\"USDA FoodData Central\",\"sourceId\":\"" + sourceId + "\",\"quantityGrams\":125,\"calories\":99999,\"userId\":\"somebody-else\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.sourceId").value(sourceId))
                    .andExpect(jsonPath("$.calories").value(org.hamcrest.Matchers.not(99999)));
            mvc.perform(get("/api/meals/today").header("Authorization", authorization))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
            mvc.perform(post("/api/chat").header("Authorization", authorization).contentType("application/json")
                            .content("{\"message\":\"How much protein does this food contain?\",\"source\":\"USDA FoodData Central\",\"sourceId\":\"" + sourceId + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Source ID: " + sourceId)));
            System.out.println("LIVE WORKFLOW: USDA search, exact refetch, meal persistence, chat with/without evidence PASS; FDC ID " + sourceId);
            mvc.perform(post("/api/chat").header("Authorization", authorization).contentType("application/json")
                            .content("{\"message\":\"Suggest a vegetarian breakfast and explain using my stated preference.\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.reply").isString());
            System.out.println("LIVE WORKFLOW: Groq qualitative response PASS (not a semantic correctness certification)");
        } finally {
            // DATABASE is generated by this test, never read from application/user configuration.
            if (DATABASE.equals(mongo.getDb().getName()) && DATABASE.startsWith("nutriverse_audit_")) {
                mongo.getDb().drop();
                System.out.println("LIVE WORKFLOW: isolated synthetic database removed");
            }
        }
    }
}
