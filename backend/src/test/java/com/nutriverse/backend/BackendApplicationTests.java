package com.nutriverse.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "JWT_SECRET=test-only-signing-key-with-at-least-32-bytes",
        "GROQ_API_KEY=test-key", "USDA_API_KEY=test-key", "NEO4J_PASSWORD=test-password",
        "spring.mongodb.uri=mongodb://localhost:27017/?serverSelectionTimeoutMS=500",
        "spring.mongodb.database=nutriverse_test", "logging.level.root=WARN", "debug=false",
        "logging.level.org.springframework.web=INFO", "logging.level.org.springframework.boot=INFO"
})
class BackendApplicationTests {
    @Autowired
    WebApplicationContext context;

	@Test
	void contextLoads() {
	}

    @Test
    void realMvcConfigurationProtectsPrivateRoutesAndAllowsPublicValidation() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (String path : new String[]{"/api/dashboard", "/api/profile", "/api/meals", "/api/water",
                "/api/kg/foods", "/api/nutrition/search?query=banana", "/api/dashboard;version=1"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(options("/api/profile").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/profile").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }

}
