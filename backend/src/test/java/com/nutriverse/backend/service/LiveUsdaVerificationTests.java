package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Locale;
import java.util.Map;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: uses the real USDA service and consumes the configured API quota. */
@EnabledIfEnvironmentVariable(named = "NUTRIVERSE_LIVE_USDA", matches = "true")
class LiveUsdaVerificationTests {
    @Test
    void bananaSearchAndExactFdcIdHaveMatchingGovernmentProvenanceAndNutrients() {
        String key = System.getenv("USDA_API_KEY");
        assertNotNull(key, "USDA_API_KEY must be configured for live verification");
        assertFalse(key.isBlank(), "USDA_API_KEY must be configured for live verification");
        UsdaFoodDataProvider provider = new UsdaFoodDataProvider("https://api.nal.usda.gov/fdc/v1", key);
        NutritionResult selected = provider.search("banana").stream()
                .filter(food -> food.getFoodName().toLowerCase(Locale.ROOT).contains("banana"))
                .findFirst().orElseThrow(() -> new AssertionError("No usable banana result from USDA"));
        assertTrue(selected.getSourceId().matches("[1-9][0-9]*"));
        NutritionResult fetched = provider.findBySourceId(selected.getSourceId());
        if (fetched == null) {
            diagnoseDetails(selected, key);
        }
        assertNotNull(fetched, "Exact USDA lookup failed for FDC " + selected.getSourceId()
                + " (" + selected.getDataType() + ")");
        assertEquals(selected.getSourceId(), fetched.getSourceId());
        assertEquals(selected.getFoodName(), fetched.getFoodName());
        assertEquals("USDA FoodData Central", fetched.getSource());
        assertEquals("AUTHORITATIVE_DATABASE", fetched.getSourceType());
        assertTrue(fetched.isVerified());
        assertFalse(fetched.isEstimated());
        assertEquals(100.0, fetched.getServingSize());
        assertEquals("g", fetched.getServingUnit());
        assertEquals(
                selected.getCalories(),
                fetched.getCalories(),
                0.1
        );

        assertEquals(
                selected.getProtein(),
                fetched.getProtein(),
                0.1
        );

        assertEquals(
                selected.getCarbs(),
                fetched.getCarbs(),
                0.1
        );

        assertEquals(
                selected.getFat(),
                fetched.getFat(),
                0.1
        );
    }

    private void diagnoseDetails(NutritionResult selected, String key) {
        try {
            SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
            requests.setConnectTimeout(5000);
            requests.setReadTimeout(10000);
            Map<?, ?> response = RestClient.builder().baseUrl("https://api.nal.usda.gov/fdc/v1")
                    .requestFactory(requests).build().get()
                    .uri(uri -> uri.path("/food/{id}").queryParam("api_key", "{key}")
                            .build(selected.getSourceId(), key))
                    .retrieve().body(Map.class);
            if (response == null) {
                return;
            }
            System.out.printf("USDA public food: id=%s dataType=%s unit=%s%n",
                    response.get("fdcId"), response.get("dataType"), response.get("servingSizeUnit"));
            if (response.get("foodNutrients") instanceof List<?> nutrients) {
                for (Object item : nutrients.stream().limit(6).toList()) {
                    if (item instanceof Map<?, ?> row) {
                        System.out.printf("USDA public nutrient: keys=%s nutrient=%s amount=%s value=%s%n",
                                row.keySet(), row.get("nutrient"), row.get("amount"), row.get("value"));
                    }
                }
            }
        } catch (Exception error) {
            System.out.println("USDA diagnostic failed: " + error.getClass().getSimpleName());
        }
    }
}
