package com.nutriverse.backend.service;

import com.nutriverse.backend.model.MealLog;
import com.nutriverse.backend.repository.MealLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MealLogService {

    private final MealLogRepository mealLogRepository;

    public MealLogService(MealLogRepository mealLogRepository) {
        this.mealLogRepository = mealLogRepository;
    }

    public List<MealLog> getTodayMeals(String userId) {

        LocalDate today = LocalDate.now();

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        return mealLogRepository
                .findByUserIdAndLoggedAtBetween(
                        userId,
                        start,
                        end
                );
    }

    public List<MealLog> getAllMeals(String userId) {
        return mealLogRepository
                .findByUserIdOrderByLoggedAtDesc(userId);
    }
}
