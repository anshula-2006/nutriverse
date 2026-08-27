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


    // =========================================================
    // GET COMPLETE DASHBOARD
    // =========================================================

    public DashboardResponse getDashboard(String userId) {

        DashboardResponse dashboard =
                new DashboardResponse();


        // -----------------------------------------------------
        // 1. USER
        // -----------------------------------------------------

        User user =
                userRepository
                        .findById(userId)
                        .orElse(null);

        if (user != null) {
            dashboard.setName(user.getName());
        }


        // -----------------------------------------------------
        // 2. PROFILE
        // -----------------------------------------------------

        NutritionProfile profile =
                profileRepository
                        .findByUserId(userId)
                        .orElse(null);

        if (profile != null &&
                (
                        profile.getDailyCalorieTarget() == null ||
                                profile.getDailyProteinTarget() == null ||
                                profile.getDailyWaterTarget() == null
                )
        ) {

            profile =
                    dailyTargetService
                            .calculateTargets(userId);
        }
        if (profile != null) {

            dashboard.setGoal(
                    profile.getGoal()
            );

            dashboard.setDietType(
                    profile.getDietType()
            );

            dashboard.setCalorieTarget(
                    profile.getDailyCalorieTarget()
            );

            dashboard.setProteinTarget(
                    profile.getDailyProteinTarget()
            );

            dashboard.setWaterTarget(
                    profile.getDailyWaterTarget()
            );


            // BMI
            calculateBmi(
                    profile,
                    dashboard
            );
        }


        // -----------------------------------------------------
        // 3. TODAY'S MEALS
        // -----------------------------------------------------

        LocalDate today =
                LocalDate.now();

        LocalDateTime start =
                today.atStartOfDay();

        LocalDateTime end =
                today.plusDays(1)
                        .atStartOfDay();


        List<MealLog> todayMeals =
                mealLogRepository
                        .findByUserIdAndLoggedAtBetween(
                                userId,
                                start,
                                end
                        );


        dashboard.setTodayMeals(
                todayMeals
        );


        // -----------------------------------------------------
        // 4. TODAY'S NUTRITION TOTALS
        // -----------------------------------------------------

        double calories = 0;
        double protein = 0;
        double carbs = 0;
        double fat = 0;


        for (MealLog meal : todayMeals) {

            if (meal.getCalories() != null) {
                calories += meal.getCalories();
            }

            if (meal.getProtein() != null) {
                protein += meal.getProtein();
            }

            if (meal.getCarbs() != null) {
                carbs += meal.getCarbs();
            }

            if (meal.getFat() != null) {
                fat += meal.getFat();
            }
        }


        dashboard.setCaloriesConsumed(
                round(calories)
        );

        dashboard.setProteinConsumed(
                round(protein)
        );

        dashboard.setCarbsConsumed(
                round(carbs)
        );

        dashboard.setFatConsumed(
                round(fat)
        );


        // -----------------------------------------------------
        // 5. WATER CONSUMED TODAY
        // -----------------------------------------------------

        double water =
                waterLogService
                        .getTodayTotal(userId);

        dashboard.setWaterConsumed(
                round(water)
        );


        // -----------------------------------------------------
        // 6. WEEKLY CALORIE GRAPH
        // -----------------------------------------------------

        dashboard.setWeeklyCalories(
                calculateWeeklyCalories(
                        userId
                )
        );


        return dashboard;
    }


    // =========================================================
    // BMI
    // =========================================================

    private void calculateBmi(
            NutritionProfile profile,
            DashboardResponse dashboard
    ) {

        if (
                profile.getHeight() == null ||
                        profile.getWeight() == null
        ) {
            dashboard.setBmi(0);
            dashboard.setBmiCategory(
                    "Not Available"
            );

            return;
        }


        double heightInMeters =
                profile.getHeight() / 100.0;


        double bmi =
                profile.getWeight() /
                        (
                                heightInMeters *
                                        heightInMeters
                        );


        bmi = round(bmi);


        dashboard.setBmi(bmi);


        if (bmi < 18.5) {

            dashboard.setBmiCategory(
                    "Underweight"
            );

        } else if (bmi < 25) {

            dashboard.setBmiCategory(
                    "Normal"
            );

        } else if (bmi < 30) {

            dashboard.setBmiCategory(
                    "Overweight"
            );

        } else {

            dashboard.setBmiCategory(
                    "Obese"
            );
        }
    }


    // =========================================================
    // WEEKLY CALORIE TOTALS
    // =========================================================

    private Map<String, Double> calculateWeeklyCalories(
            String userId
    ) {

        Map<String, Double> weeklyCalories =
                new LinkedHashMap<>();


        LocalDate today =
                LocalDate.now();


        // Previous 6 days + today
        for (int i = 6; i >= 0; i--) {

            LocalDate date =
                    today.minusDays(i);


            LocalDateTime start =
                    date.atStartOfDay();

            LocalDateTime end =
                    date.plusDays(1)
                            .atStartOfDay();


            List<MealLog> meals =
                    mealLogRepository
                            .findByUserIdAndLoggedAtBetween(
                                    userId,
                                    start,
                                    end
                            );


            double totalCalories =
                    meals.stream()
                            .filter(
                                    meal ->
                                            meal.getCalories()
                                                    != null
                            )
                            .mapToDouble(
                                    MealLog::getCalories
                            )
                            .sum();


            String day =
                    date.getDayOfWeek()
                            .getDisplayName(
                                    TextStyle.SHORT,
                                    Locale.ENGLISH
                            )
                            .toUpperCase();


            weeklyCalories.put(
                    day,
                    round(totalCalories)
            );
        }


        return weeklyCalories;
    }


    // =========================================================
    // ROUND TO 1 DECIMAL
    // =========================================================

    private double round(double value) {

        return Math.round(
                value * 10.0
        ) / 10.0;
    }
}