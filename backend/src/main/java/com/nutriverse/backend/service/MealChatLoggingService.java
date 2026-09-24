package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.ChatResponse;
import com.nutriverse.backend.dto.NutritionMealRequest;
import com.nutriverse.backend.dto.NutritionResult;
import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.repository.MealLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MealChatLoggingService {
    private static final Pattern INTENT = Pattern.compile("(?i)\\b(?:i\\s+)?(?:ate|had|consumed)\\b");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*(.*)$");
    private static final Pattern MASS = Pattern.compile("(?i)^(g|gram|grams|kg|kilogram|kilograms)\\s+(.+)$");
    private static final Pattern ONLY_AMOUNT = Pattern.compile("(?i)^([0-9]+(?:\\.[0-9]+)?)\\s*(g|gram|grams|kg|kilogram|kilograms)?$");
    private static final Map<String, Double> PIECE_GRAMS = Map.of("idli", 50.0);

    private final NutritionLookupService lookup;
    private final NutritionMealService mealService;
    private final MealLogRepository mealRepository;
    private final ChatMemory memory;
    private final Map<String, PendingMeal> pending = new ConcurrentHashMap<>();

    public MealChatLoggingService(
            NutritionLookupService lookup,
            NutritionMealService mealService,
            MealLogRepository mealRepository,
            ChatMemory memory
    ) {
        this.lookup = lookup;
        this.mealService = mealService;
        this.mealRepository = mealRepository;
        this.memory = memory;
    }

    public ChatResponse handle(String userId, String message) {
        if (message == null || message.isBlank()) return null;

        PendingMeal waiting = pending.get(userId);
        if (waiting != null) {
            ChatResponse response = continuePending(userId, message.trim(), waiting);
            if (response != null) return response;
        }

        if (!INTENT.matcher(message).find()) return null;
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("yesterday") || lower.contains("tomorrow")) {
            return reply(userId, message, "Chat meal logging currently supports today's meals only.");
        }

        ParsedMeal parsed = parse(message);
        if (parsed == null || parsed.food().isBlank()) return null;

        if (parsed.mealType() == null) {
            PendingMeal item = new PendingMeal(parsed.food(), parsed.amount(), parsed.unit(), null);
            pending.put(userId, item);
            return reply(userId, message, "Which meal should I log it under: breakfast, lunch, dinner, or snack?");
        }

        if (parsed.amount() == null) {
            pending.put(userId, new PendingMeal(parsed.food(), null, null, parsed.mealType()));
            return reply(userId, message, "How much did you have? You can reply with a count or grams, for example 4 or 200 g.");
        }

        return log(userId, message, parsed.food(), parsed.amount(), parsed.unit(), parsed.mealType());
    }

    private ChatResponse continuePending(String userId, String message, PendingMeal item) {
        String mealType = mealType(message);
        if (item.mealType() == null && mealType != null) {
            PendingMeal updated = new PendingMeal(item.food(), item.amount(), item.unit(), mealType);
            if (updated.amount() == null) {
                pending.put(userId, updated);
                return reply(userId, message, "How much did you have? You can reply with a count or grams, for example 4 or 200 g.");
            }
            pending.remove(userId);
            return log(userId, message, updated.food(), updated.amount(), updated.unit(), mealType);
        }

        if (item.amount() == null) {
            Matcher matcher = ONLY_AMOUNT.matcher(message);
            if (!matcher.matches()) return null;
            double amount = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            pending.remove(userId);
            return log(userId, message, item.food(), amount, unit, item.mealType());
        }

        return null;
    }

    private ParsedMeal parse(String message) {
        Matcher intent = INTENT.matcher(message);
        if (!intent.find()) return null;

        String remainder = message.substring(intent.end()).trim();
        String mealType = mealType(message);
        remainder = remainder.replaceAll("(?i)\\s+for\\s+(?:breakfast|lunch|dinner|snack).*$", "").trim();
        remainder = remainder.replaceAll("(?i)\\s+(?:today|tonight|this morning|this afternoon|this evening)[.!?]*$", "").trim();
        remainder = remainder.replaceAll("[.!?]+$", "").trim();

        Matcher number = LEADING_NUMBER.matcher(remainder);
        if (!number.matches()) return new ParsedMeal(singular(remainder), null, null, mealType);

        double amount = Double.parseDouble(number.group(1));
        String rest = number.group(2).trim();
        Matcher mass = MASS.matcher(rest);
        if (mass.matches()) {
            return new ParsedMeal(singular(mass.group(2).trim()), amount, mass.group(1), mealType);
        }
        return new ParsedMeal(singular(rest), amount, null, mealType);
    }

    private ChatResponse log(
            String userId,
            String userMessage,
            String foodName,
            double amount,
            String unit,
            String mealType
    ) {
        Quantity quantity = toGrams(foodName, amount, unit);
        if (quantity == null) {
            pending.put(userId, new PendingMeal(foodName, null, null, mealType));
            return reply(userId, userMessage,
                    "I can log " + foodName + ", but I need its approximate weight in grams. For example: 200 g.");
        }

        NutritionResult food = best(foodName);
        if (food == null) {
            return reply(userId, userMessage,
                    "I couldn't find a verified nutrition record for " + foodName + ". Try a simpler food name.");
        }

        NutritionMealRequest request = new NutritionMealRequest();
        request.setMealType(mealType);
        request.setSource(food.getSource());
        request.setSourceId(food.getSourceId());
        request.setQuantityGrams(quantity.grams());

        MealLog saved = mealService.logMeal(userId, request);
        if (quantity.estimated()) {
            saved.setEstimated(true);
            Double confidence = saved.getConfidence();
            saved.setConfidence(confidence == null ? 0.85 : Math.min(confidence, 0.85));
            mealRepository.save(saved);
        }

        pending.remove(userId);
        String meal = mealType.toLowerCase(Locale.ROOT);
        String text = "Logged " + displayAmount(amount, unit, foodName)
                + " to today's " + meal + "."
                + " Nutrition source: " + saved.getSource() + ".";
        if (quantity.estimated()) {
            text += " Portion weight was estimated at 50 g per idli (~"
                    + clean(quantity.grams()) + " g total).";
        }
        return reply(userId, userMessage, text);
    }

    private NutritionResult best(String foodName) {
        try {
            List<NutritionResult> results = lookup.search(foodName);
            if (results == null) return null;
            return results.stream().filter(this::loggable).findFirst().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean loggable(NutritionResult food) {
        return food != null && food.isVerified() && !food.isEstimated()
                && food.getSource() != null && food.getSourceId() != null
                && food.getServingSize() != null && food.getServingSize() > 0
                && "g".equalsIgnoreCase(food.getServingUnit())
                && ("AUTHORITATIVE_DATABASE".equals(food.getSourceType())
                || "PRODUCT_DATABASE".equals(food.getSourceType()));
    }

    private Quantity toGrams(String foodName, double amount, String unit) {
        if (!Double.isFinite(amount) || amount <= 0 || amount > 10000) return null;
        if (unit != null) {
            String normalized = unit.toLowerCase(Locale.ROOT);
            double grams = normalized.startsWith("kg") ? amount * 1000.0 : amount;
            return grams <= 10000 ? new Quantity(grams, false) : null;
        }

        Double piece = PIECE_GRAMS.get(singular(foodName));
        if (piece == null) return null;
        double grams = amount * piece;
        return grams <= 10000 ? new Quantity(grams, true) : null;
    }

    private String mealType(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        if (value.matches(".*\\bbreakfast\\b.*")) return "BREAKFAST";
        if (value.matches(".*\\blunch\\b.*")) return "LUNCH";
        if (value.matches(".*\\bdinner\\b.*")) return "DINNER";
        if (value.matches(".*\\bsnack\\b.*")) return "SNACK";
        return null;
    }

    private String singular(String value) {
        String food = value.toLowerCase(Locale.ROOT).trim();
        if (food.endsWith("ies") && food.length() > 3) return food.substring(0, food.length() - 3) + "y";
        if (food.endsWith("is")) return food;
        if (food.endsWith("s") && food.length() > 3) return food.substring(0, food.length() - 1);
        return food;
    }

    private ChatResponse reply(String userId, String userMessage, String text) {
        memory.addMessage(userId, "user", userMessage);
        memory.addMessage(userId, "assistant", text);
        return new ChatResponse(text);
    }

    private String displayAmount(double amount, String unit, String food) {
        if (unit != null) return clean(amount) + " " + unit + " " + food;
        return clean(amount) + " " + (amount == 1 ? food : food + "s");
    }

    private String clean(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private record ParsedMeal(String food, Double amount, String unit, String mealType) {}
    private record PendingMeal(String food, Double amount, String unit, String mealType) {}
    private record Quantity(double grams, boolean estimated) {}
}
