package com.nutriverse.backend.service;
import com.nutriverse.backend.dto.NutritionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class NutritionLookupService {
    private static final Logger log =
            LoggerFactory.getLogger(NutritionLookupService.class);
    private static final int MAX_RESULTS = 20;
    private static final String IFCT = "ICMR-NIN IFCT 2017";
    private static final String USDA = "USDA FoodData Central";
    private static final String OFF = "Open Food Facts";
    private static final Map<String, String> USDA_ALIASES = Map.ofEntries(
            Map.entry("green gram", "mung beans"),
            Map.entry("moong", "mung"),
            Map.entry("toor", "pigeon pea"),
            Map.entry("rajma", "kidney beans"),
            Map.entry("chana", "chickpea"),
            Map.entry("curd", "yogurt"),
            Map.entry("ragi", "finger millet"),
            Map.entry("bajra", "pearl millet"),
            Map.entry("jowar", "sorghum")
    );
    private final IfctFoodDataProvider ifct;
    private final UsdaFoodDataProvider usda;
    private final OpenFoodFactsProvider off;
    @Autowired
    public NutritionLookupService(
            IfctFoodDataProvider ifct,
            UsdaFoodDataProvider usda,
            OpenFoodFactsProvider off
    ) {
        this.ifct = ifct;
        this.usda = usda;
        this.off = off;
    }
    NutritionLookupService(
            UsdaFoodDataProvider usda,
            OpenFoodFactsProvider off
    ) {
        this.ifct = null;
        this.usda = usda;
        this.off = off;
    }
    public List<NutritionResult> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        if (query.length() > 200) {
            throw new IllegalArgumentException("Search query is too long");
        }
        String original = query.trim();
        String usdaQuery = normalizeForUsda(original);
        List<NutritionResult> results = new ArrayList<>();
        boolean providerFailed = false;
        if (ifct != null) {
            providerFailed |= addResults(ifct, original, results);
        }
        if (usda != null) {
            providerFailed |= addResults(usda, usdaQuery, results);
        }
        if (off != null) {
            providerFailed |= addResults(off, original, results);
        }
        if (results.isEmpty() && providerFailed) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Nutrition search is temporarily unavailable. " +
                            "Please try again shortly."
            );
        }
        return rankAndDeduplicate(results, original, usdaQuery);
    }
    private boolean addResults(
            NutritionProvider provider,
            String query,
            List<NutritionResult> results
    ) {
        try {
            List<NutritionResult> found = provider.search(query);
            if (found == null) return true;
            results.addAll(found);
            return false;
        } catch (RuntimeException e) {
            log.warn(
                    "{} search failed: {}",
                    providerName(provider),
                    e.getClass().getSimpleName()
            );
            return true;
        }
    }
    public NutritionResult findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank() || off == null) {
            return null;
        }
        return off.findByBarcode(barcode.trim());
    }
    public NutritionResult findBySourceId(String source, String sourceId) {
        if (source == null || sourceId == null || sourceId.isBlank()) {
            return null;
        }
        NutritionProvider provider = providerFor(source.trim());
        if (provider == null) return null;
        NutritionResult result = provider.findBySourceId(sourceId.trim());
        if (result == null) return null;
        if (result.getSourceId() == null
                || !sourceId.trim().equalsIgnoreCase(result.getSourceId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nutrition provider returned a different food."
            );
        }
        String expectedSource = provider.getProviderName();
        if (expectedSource != null
                && result.getSource() != null
                && !expectedSource.equalsIgnoreCase(result.getSource())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nutrition provider returned an unexpected source."
            );
        }
        return result;
    }
    private List<NutritionResult> rankAndDeduplicate(
            List<NutritionResult> foods,
            String original,
            String usdaQuery
    ) {
        Map<String, NutritionResult> unique = new LinkedHashMap<>();
        int unknown = 0;
        for (NutritionResult food : foods) {
            if (food == null) continue;
            String key;
            if (food.getSource() != null && food.getSourceId() != null) {
                key = normalize(food.getSource()) + ":"
                        + normalize(food.getSourceId());
            } else {
                key = "unknown:" + unknown++;
            }
            unique.putIfAbsent(key, food);
        }
        return unique.values().stream()
                .sorted(Comparator.comparingInt(
                        (NutritionResult food) ->
                                relevance(food, original, usdaQuery)
                ).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }
    private int relevance(
            NutritionResult food,
            String original,
            String usdaQuery
    ) {
        if (food.getFoodName() == null) return 0;
        String foodName = normalize(food.getFoodName());
        String originalSearch = normalize(original);
        String usdaSearch = normalize(usdaQuery);
        int score = textMatchScore(foodName, originalSearch);
        if (!originalSearch.equals(usdaSearch)) {
            score += normalizedMatchScore(foodName, usdaSearch);
        }
        if (food.isVerified()) score += 2;
        if ("AUTHORITATIVE_DATABASE".equals(food.getSourceType())) {
            score += 1;
        }
        if (IFCT.equals(food.getSource())
                && ifct != null
                && ifct.matchesIndexedAlias(
                food.getSourceId(),
                original
        )) {
            score += 24;
        }
        if ("PRODUCT_DATABASE".equals(food.getSourceType())) {
            score -= 6;
        }
        return score;
    }
    private int textMatchScore(String foodName, String query) {
        if (query.isBlank()) return 0;
        if (foodName.equals(query)) return 30;
        if (foodName.startsWith(query)) return 20;
        if (foodName.contains(query)) return 12;
        int score = 0;
        for (String word : query.split(" ")) {
            if (word.length() >= 3 && foodName.contains(word)) {
                score += 3;
            }
        }
        return score;
    }
    private int normalizedMatchScore(String foodName, String query) {
        if (query.isBlank()) return 0;
        if (foodName.equals(query)) return 18;
        if (foodName.startsWith(query)) return 12;
        if (foodName.contains(query)) return 8;
        int score = 0;
        for (String word : query.split(" ")) {
            if (word.length() >= 3 && foodName.contains(word)) {
                score += 2;
            }
        }
        return score;
    }
    private String normalizeForUsda(String query) {
        String result = query.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : USDA_ALIASES.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result.trim();
    }
    private NutritionProvider providerFor(String source) {
        if (ifct != null
                && ("IFCT".equalsIgnoreCase(source)
                || IFCT.equalsIgnoreCase(source))) {
            return ifct;
        }
        if (usda != null
                && ("USDA".equalsIgnoreCase(source)
                || USDA.equalsIgnoreCase(source))) {
            return usda;
        }
        if (off != null
                && ("OFF".equalsIgnoreCase(source)
                || OFF.equalsIgnoreCase(source))) {
            return off;
        }
        return null;
    }
    private String providerName(NutritionProvider provider) {
        String name = provider.getProviderName();
        return name == null
                ? provider.getClass().getSimpleName()
                : name;
    }
    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                .replace("-", " ")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
