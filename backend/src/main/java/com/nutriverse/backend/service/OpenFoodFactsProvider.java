package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OpenFoodFactsProvider implements NutritionProvider {
    private static final Logger logger = LoggerFactory.getLogger(OpenFoodFactsProvider.class);
    private static final Pattern MASS = Pattern.compile("(?i)\\d[\\d.,]*\\s*(?:kg|mg|g|grams?|kilograms?)\\b");
    private static final Pattern VOLUME = Pattern.compile("(?i)(?<![a-z])(?:\\d[\\d.,]*\\s*)?(?:ml|cl|dl|l|litres?|liters?|fl\\.?\\s*oz)\\b");
    private final RestClient productClient;
    private final RestClient searchClient;

    public OpenFoodFactsProvider() {
        this(productClient("https://world.openfoodfacts.org"), productClient("https://search.openfoodfacts.org"));
    }

    OpenFoodFactsProvider(RestClient productClient, RestClient searchClient) {
        this.productClient = productClient;
        this.searchClient = searchClient;
    }

    private static RestClient productClient(String url) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(5000);
        requests.setReadTimeout(10000);
        return RestClient.builder().baseUrl(url).requestFactory(requests)
                .defaultHeader("User-Agent", "NutriVerse/1.0 Academic Project").build();
    }

    @Override
    public List<NutritionResult> search(String query) {
        List<NutritionResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }
        try {
            Map<?, ?> response = searchClient.post().uri("/search").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("q", query.trim(), "page_size", 10, "page", 1))
                    .retrieve().body(Map.class);
            if (response == null || !(response.get("hits") instanceof List<?> hits)) {
                throw new IllegalStateException("Missing product search results");
            }
            Set<String> sourceIds = new HashSet<>();
            for (Object object : hits) {
                if (!(object instanceof Map<?, ?> hit)) {
                    continue;
                }
                Map<?, ?> product = hit.get("_source") instanceof Map<?, ?> source ? source : hit;
                NutritionResult result = convertProduct(product);
                if (result != null && sourceIds.add(result.getSourceId())) {
                    results.add(result);
                }
            }
            return results;
        } catch (Exception error) {
            throw unavailable("search", error);
        }
    }

    @Override
    public NutritionResult findByBarcode(String barcode) {
        return fetchProduct(barcode);
    }

    @Override
    public NutritionResult findBySourceId(String sourceId) {
        return fetchProduct(sourceId);
    }

    private NutritionResult fetchProduct(String code) {
        if (code == null || !code.trim().matches("[0-9]{1,24}")) {
            return null;
        }
        try {
            Map<?, ?> response = productClient.get().uri("/api/v2/product/{code}.json", code.trim())
                    .retrieve().body(Map.class);
            if (response != null && Integer.valueOf(0).equals(response.get("status"))) {
                return null;
            }
            if (response == null || !(response.get("product") instanceof Map<?, ?> product)
                    || !code.trim().equals(text(product.get("code")))) {
                throw new IllegalStateException("Missing or mismatched product identifier");
            }
            return convertProduct(product);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) {
                return null;
            }
            throw unavailable("lookup", error);
        } catch (Exception error) {
            throw unavailable("lookup", error);
        }
    }

    private NutritionResult convertProduct(Map<?, ?> product) {
        String name = text(product.get("product_name"));
        String code = text(product.get("code"));
        if (name == null || code == null || !code.matches("[0-9]{1,24}")
                || !(product.get("nutriments") instanceof Map<?, ?> nutrients)
                || !hasMassBasis(product)) {
            return null;
        }
        NutritionResult result = new NutritionResult();
        String brand = text(product.get("brands"));
        result.setFoodName(brand == null ? name : brand + " " + name);
        result.setCalories(number(nutrients.get("energy-kcal_100g")));
        result.setProtein(number(nutrients.get("proteins_100g")));
        result.setCarbs(number(nutrients.get("carbohydrates_100g")));
        result.setFat(number(nutrients.get("fat_100g")));
        result.setFiber(number(nutrients.get("fiber_100g")));
        // OFF normalizes weight nutrients to grams; NutriVerse exposes minerals in mg.
        result.setIron(milligrams(nutrients.get("iron_100g")));
        result.setCalcium(milligrams(nutrients.get("calcium_100g")));
        result.setSodium(milligrams(nutrients.get("sodium_100g")));
        result.setPotassium(milligrams(nutrients.get("potassium_100g")));
        if (result.getCalories() == null && result.getProtein() == null
                && result.getCarbs() == null && result.getFat() == null) {
            return null;
        }
        result.setServingSize(100.0);
        result.setServingUnit("g");
        result.setSourceType("PRODUCT_DATABASE");
        result.setSource("Open Food Facts");
        result.setSourceId(code);
        result.setVerified(false);
        result.setEstimated(false);
        return result;
    }

    private boolean hasMassBasis(Map<?, ?> product) {
        // OFF's _100g key also represents 100 ml for liquids. Require explicit mass
        // evidence; nutrition_data_per=100g alone does not establish the physical unit.
        boolean mass = false;
        for (String key : List.of("product_quantity_unit", "serving_quantity_unit", "quantity",
                "serving_size", "nutrition_data_per")) {
            String value = text(product.get(key));
            if (value == null) {
                continue;
            }
            value = value.toLowerCase(Locale.ROOT);
            if (VOLUME.matcher(value).find()) {
                return false;
            }
            if (!"nutrition_data_per".equals(key)) {
                mass |= value.matches("g|kg|mg|grams?|kilograms?") || MASS.matcher(value).find();
            }
        }
        return mass;
    }

    private Double milligrams(Object value) {
        Double grams = number(value);
        if (grams == null || !Double.isFinite(grams * 1000.0)) {
            return null;
        }
        return grams * 1000.0;
    }

    private Double number(Object value) {
        try {
            if (value == null) {
                return null;
            }
            double parsed = value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString());
            return Double.isFinite(parsed) && parsed >= 0 ? parsed : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private String text(Object value) {
        if (!(value instanceof String || value instanceof Number)) {
            return null;
        }
        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private ResponseStatusException unavailable(String operation, Exception error) {
        String status = error instanceof RestClientResponseException response
                ? Integer.toString(response.getStatusCode().value()) : "unavailable";
        logger.warn("Open Food Facts {} failed: status={} type={}", operation, status, error.getClass().getSimpleName());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Open Food Facts is unavailable. Please try again shortly.");
    }

    @Override
    public String getProviderName() {
        return "Open Food Facts";
    }
}
