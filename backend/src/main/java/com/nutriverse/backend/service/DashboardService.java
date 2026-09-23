package com.nutriverse.backend.service;

import com.nutriverse.backend.dto.DashboardResponse;
import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.model.NutritionProfile;
import com.nutriverse.backend.model.User;
import com.nutriverse.backend.repository.MealLogRepository;
import com.nutriverse.backend.repository.NutritionProfileRepository;
import com.nutriverse.backend.repository.UserRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final NutritionProfileRepository profileRepository;
    private final MealLogRepository mealLogRepository;
    private final WaterLogService waterLogService;
    private final DailyTargetService dailyTargetService;

    public DashboardService(
            UserRepository userRepository,
            NutritionProfileRepository profileRepository,
            MealLogRepository mealLogRepository,
            WaterLogService waterLogService,
            DailyTargetService dailyTargetService
    ) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.mealLogRepository = mealLogRepository;
        this.waterLogService = waterLogService;
        this.dailyTargetService = dailyTargetService;
    }

    public DashboardResponse getDashboard(String userId) {
        DashboardResponse dashboard = new DashboardResponse();

        loadUser(userId, dashboard);
        loadProfile(userId, dashboard);

        List<MealLog> todayMeals = getMealsForDate(
                userId,
                LocalDate.now()
        );

        dashboard.setTodayMeals(todayMeals);
        applyNutritionTotals(todayMeals, dashboard);

        dashboard.setWaterConsumed(
                round(waterLogService.getTodayTotal(userId))
        );

        dashboard.setWeeklyCalories(
                calculateWeeklyCalories(userId)
        );

        return dashboard;
    }

    private void loadUser(
            String userId,
            DashboardResponse dashboard
    ) {
        User user = userRepository
                .findById(userId)
                .orElse(null);

        if (user != null) {
            dashboard.setName(user.getName());
        }
    }

    private void loadProfile(
            String userId,
            DashboardResponse dashboard
    ) {
        NutritionProfile profile = profileRepository
                .findByUserId(userId)
                .orElse(null);

        if (profile == null) {
            return;
        }

        profile = dailyTargetService.calculateTargets(userId);

        if (profile == null) {
            return;
        }

        dashboard.setGoal(profile.getGoal());
        dashboard.setDietType(profile.getDietType());
        dashboard.setCalorieTarget(
                profile.getDailyCalorieTarget()
        );
        dashboard.setProteinTarget(
                profile.getDailyProteinTarget()
        );
        dashboard.setWaterTarget(
                profile.getDailyWaterTarget()
        );

        calculateBmi(profile, dashboard);
    }

    private void applyNutritionTotals(
            List<MealLog> meals,
            DashboardResponse dashboard
    ) {
        double calories = 0;
        double protein = 0;
        double carbs = 0;
        double fat = 0;

        for (MealLog meal : meals) {
            if (isIncomplete(meal)) {
                dashboard.setNutritionIncomplete(true);
            }

            if (validNutrient(meal.getCalories())) {
                calories += meal.getCalories();
            }

            if (validNutrient(meal.getProtein())) {
                protein += meal.getProtein();
            }

            if (validNutrient(meal.getCarbs())) {
                carbs += meal.getCarbs();
            }

            if (validNutrient(meal.getFat())) {
                fat += meal.getFat();
            }
        }

        dashboard.setCaloriesConsumed(round(calories));
        dashboard.setProteinConsumed(round(protein));
        dashboard.setCarbsConsumed(round(carbs));
        dashboard.setFatConsumed(round(fat));
    }

    private boolean isIncomplete(MealLog meal) {
        return !validNutrient(meal.getCalories())
                || !validNutrient(meal.getProtein())
                || !validNutrient(meal.getCarbs())
                || !validNutrient(meal.getFat())
                || meal.getSourceId() == null
                || meal.getSource() == null;
    }

    private void calculateBmi(
            NutritionProfile profile,
            DashboardResponse dashboard
    ) {
        if (!validProfileForBmi(profile)) {
            dashboard.setBmi(0);
            dashboard.setBmiCategory("Not Available");
            return;
        }

        double heightMeters =
                profile.getHeight() / 100.0;

        double bmi = profile.getWeight()
                / (heightMeters * heightMeters);

        bmi = round(bmi);

        dashboard.setBmi(bmi);
        dashboard.setBmiCategory(
                bmiCategory(bmi)
        );
    }

    private boolean validProfileForBmi(
            NutritionProfile profile
    ) {
        return profile.getHeight() != null
                && profile.getWeight() != null
                && profile.getAge() != null
                && Double.isFinite(profile.getHeight())
                && Double.isFinite(profile.getWeight())
                && profile.getHeight() > 0
                && profile.getWeight() > 0
                && profile.getAge() >= 18;
    }

    private String bmiCategory(double bmi) {
        if (bmi < 18.5) {
            return "Underweight";
        }

        if (bmi < 25) {
            return "Normal";
        }

        if (bmi < 30) {
            return "Overweight";
        }

        return "Obese";
    }

    private Map<String, Double> calculateWeeklyCalories(
            String userId
    ) {
        Map<String, Double> weekly =
                new LinkedHashMap<>();

        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);

            double calories = getMealsForDate(userId, date)
                    .stream()
                    .filter(
                            meal ->
                                    validNutrient(
                                            meal.getCalories()
                                    )
                    )
                    .mapToDouble(MealLog::getCalories)
                    .sum();

            String day = date
                    .getDayOfWeek()
                    .getDisplayName(
                            TextStyle.SHORT,
                            Locale.ENGLISH
                    )
                    .toUpperCase();

            weekly.put(day, round(calories));
        }

        return weekly;
    }

    private List<MealLog> getMealsForDate(
            String userId,
            LocalDate date
    ) {
        LocalDateTime start = date.atStartOfDay();

        LocalDateTime end = date
                .plusDays(1)
                .atStartOfDay();

        return mealLogRepository
                .findByUserIdAndLoggedAtBetween(
                        userId,
                        start,
                        end
                );
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean validNutrient(Double value) {
        return value != null
                && Double.isFinite(value)
                && value >= 0;
    }
}