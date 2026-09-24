package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.NutritionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class IfctFoodDataProvider implements NutritionProvider {
    public static final String SOURCE = "ICMR-NIN IFCT 2017";
    private static final String SOURCE_TYPE = "AUTHORITATIVE_DATABASE";
    private static final Logger log = LoggerFactory.getLogger(IfctFoodDataProvider.class);

    private final Map<String, IfctFood> foods = new LinkedHashMap<>();
    private final DietaryComplianceEngine dietaryEngine;

    public IfctFoodDataProvider(DietaryComplianceEngine dietaryEngine) {
        this.dietaryEngine = dietaryEngine;
        loadAll();
    }

    @Override
    public List<NutritionResult> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        String wanted = normalizeIfctQuery(query);

        return foods.values().stream()
                .filter(food -> food.score(wanted) > 0)
                .sorted(Comparator.comparingInt(
                        (IfctFood food) -> food.score(wanted)
                ).reversed())
                .limit(25)
                .map(this::toResult)
                .toList();
    }

    @Override
    public NutritionResult findBySourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) return null;
        String code = sourceId.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-T][0-9]{3}")) return null;

        IfctFood food = foods.get(code);
        return food == null ? null : toResult(food);
    }

    @Override
    public String getProviderName() {
        return SOURCE;
    }

    boolean matchesIndexedAlias(String sourceId, String query) {
        if (sourceId == null || query == null || query.isBlank()) return false;
        IfctFood food = foods.get(sourceId.trim().toUpperCase(Locale.ROOT));
        if (food == null) return false;

        String normalized = normalizeIfctQuery(query);
        return food.isQueryCompatible(normalized)
                && food.matchesIndexedAlias(normalized);
    }

    private void loadAll() {
        try {
            loadDescriptions();
            loadCodeIndex();
            loadCore();
            loadMinerals1();
            loadMinerals2();

            foods.values().removeIf(
                    food -> food.name == null || !food.hasNutrition()
            );

            log.info("IFCT loaded successfully: {} foods", foods.size());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not load IFCT nutrition data", e
            );
        }
    }

    private void loadDescriptions() throws Exception {
        readCsv("ifct/descriptions.csv", row -> {
            if (row.size() < 5) return;
            IfctFood food = getFood(row.get(0));
            food.name = clean(row.get(1));
            food.scientificName = clean(row.get(2));
            food.group = clean(row.get(3));
            food.description = clean(row.get(4));
        });
    }

    private void loadCodeIndex() throws Exception {
        readCsv("ifct/codes-index.csv", row -> {
            if (row.size() < 2) return;
            String alias = clean(row.get(0));
            if (alias == null) return;

            for (String code : expandCodes(row.get(1))) {
                IfctFood food = foods.get(code);
                if (food != null) food.addAlias(alias);
            }
        });
    }

    private void loadCore() throws Exception {
        readCsv("ifct/core.csv", row -> {
            if (row.size() < 12) return;
            IfctFood food = getFood(row.get(0));
            if (food.name == null) food.name = clean(row.get(1));

            food.protein = number(row.get(4));
            food.fat = number(row.get(6));
            food.fiber = number(row.get(7));
            food.carbs = number(row.get(10));

            Double energyKj = number(row.get(11));
            if (energyKj != null) {
                food.calories = round(energyKj / 4.18);
            }
        });
    }

    private void loadMinerals1() throws Exception {
        readCsv("ifct/minerals1.csv", row -> {
            if (row.size() < 11) return;
            IfctFood food = getFood(row.get(0));
            food.calcium = number(row.get(6));
            food.iron = number(row.get(10));
        });
    }

    private void loadMinerals2() throws Exception {
        readCsv("ifct/minerals2.csv", row -> {
            if (row.size() < 12) return;
            IfctFood food = getFood(row.get(0));
            food.potassium = number(row.get(9));
            food.sodium = number(row.get(11));
        });
    }

    private List<String> expandCodes(String value) {
        if (value == null || value.isBlank()) return List.of();

        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String code = part.trim().toUpperCase(Locale.ROOT);

            if (code.matches("[A-T][0-9]{3}")) {
                result.add(code);
                continue;
            }

            if (!code.matches("[A-T][0-9]{3}-[A-T][0-9]{3}")) continue;

            String[] range = code.split("-");
            if (range[0].charAt(0) != range[1].charAt(0)) continue;

            char prefix = range[0].charAt(0);
            int start = Integer.parseInt(range[0].substring(1));
            int end = Integer.parseInt(range[1].substring(1));

            for (int i = start; i <= end; i++) {
                result.add(String.format("%c%03d", prefix, i));
            }
        }

        return result;
    }

    private IfctFood getFood(String rawCode) {
        String code = clean(rawCode);
        if (code == null) {
            throw new IllegalArgumentException("Missing IFCT code");
        }

        code = code.toUpperCase(Locale.ROOT);
        return foods.computeIfAbsent(code, IfctFood::new);
    }

    private NutritionResult toResult(IfctFood food) {
        NutritionResult result = new NutritionResult();

        result.setFoodName(food.name);
        result.setServingSize(100.0);
        result.setServingUnit("g");
        result.setCalories(food.calories);
        result.setProtein(food.protein);
        result.setCarbs(food.carbs);
        result.setFat(food.fat);
        result.setFiber(food.fiber);
        result.setIron(food.iron);
        result.setCalcium(food.calcium);
        result.setSodium(food.sodium);
        result.setPotassium(food.potassium);
        result.setSource(SOURCE);
        result.setSourceType(SOURCE_TYPE);
        result.setSourceId(food.code);
        result.setDataType("IFCT 2017");
        result.setVerified(true);
        result.setEstimated(false);
        result.setConfidence(1.0);

        dietaryEngine.applyNameOnly(result);
        return result;
    }

    private void readCsv(String path, RowConsumer consumer) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                )
        )) {
            String line;
            boolean header = true;

            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (!line.isBlank()) {
                    consumer.accept(parseCsv(line));
                }
            }
        }
    }

    private List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (quoted && i + 1 < line.length()
                        && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString().trim());
        return values;
    }

    private static Double number(String value) {
        String text = clean(value);
        if (text == null) return null;

        int uncertainty = text.indexOf('±');
        if (uncertainty >= 0) {
            text = text.substring(0, uncertainty).trim();
        }

        try {
            double number = Double.parseDouble(text);
            return Double.isFinite(number) && number >= 0
                    ? number
                    : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String normalizeIfctQuery(String value) {
        return normalize(value).replaceAll("\\btoor\\b", "tuvar");
    }

    private static String normalize(String value) {
        if (value == null) return "";

        return value.toLowerCase(Locale.ROOT)
                .replace("-", " ")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static class IfctFood {
        private final String code;
        private final Set<String> indexedAliases = new HashSet<>();

        private String name;
        private String scientificName;
        private String group;
        private String description;

        private Double calories;
        private Double protein;
        private Double carbs;
        private Double fat;
        private Double fiber;

        private Double iron;
        private Double calcium;
        private Double sodium;
        private Double potassium;

        private IfctFood(String code) {
            this.code = code;
        }

        private void addAlias(String alias) {
            if (alias == null || alias.isBlank()) return;

            indexedAliases.add(normalize(alias));

            String base = alias.replaceFirst(
                    "\\s*\\([^)]*\\)\\s*$", ""
            );

            if (!base.isBlank()) {
                indexedAliases.add(normalize(base));
            }
        }

        private boolean matchesIndexedAlias(String query) {
            return indexedAliases.contains(normalize(query));
        }

        private boolean isQueryCompatible(String query) {
            String q = normalize(query);
            String n = normalize(name);

            // Generic "egg/eggs" must not resolve to fish eggs/roe.
            if (q.equals("egg") || q.equals("eggs")) {
                if (containsWord(n, "fish")
                        || containsWord(n, "roe")
                        || containsWord(n, "caviar")) {
                    return false;
                }
            }

            return true;
        }

        private int score(String query) {
            String normalizedQuery = normalize(query);
            if (!isQueryCompatible(normalizedQuery)) return 0;

            String nameValue = normalize(name);

            if (nameValue.equals(normalizedQuery)) return 100;
            if (indexedAliases.contains(normalizedQuery)) return 95;
            if (nameValue.startsWith(normalizedQuery)) return 80;
            if (nameValue.contains(normalizedQuery)) return 60;

            for (String alias : indexedAliases) {
                if (alias.startsWith(normalizedQuery)) return 55;
                if (alias.contains(normalizedQuery)) return 45;
            }

            String all = searchable();
            if (all.contains(normalizedQuery)) return 40;

            for (String word : normalizedQuery.split(" ")) {
                if (word.length() >= 3 && all.contains(word)) {
                    return 20;
                }
            }

            return 0;
        }

        private String searchable() {
            return normalize(
                    safe(name) + " "
                            + safe(scientificName) + " "
                            + safe(group) + " "
                            + safe(description) + " "
                            + String.join(" ", indexedAliases)
            );
        }

        private boolean hasNutrition() {
            return calories != null
                    || protein != null
                    || carbs != null
                    || fat != null
                    || fiber != null;
        }

        private static boolean containsWord(String text, String word) {
            return (" " + text + " ").contains(" " + word + " ");
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(List<String> row);
    }
}
