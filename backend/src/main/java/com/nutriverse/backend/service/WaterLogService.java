package com.nutriverse.backend.service;

import com.nutriverse.backend.model.WaterLog;
import com.nutriverse.backend.repository.WaterLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WaterLogService {

    private final WaterLogRepository waterLogRepository;

    public WaterLogService(WaterLogRepository waterLogRepository) {
        this.waterLogRepository = waterLogRepository;
    }

    public WaterLog addWater(WaterLog waterLog) {

        if (waterLog.getLoggedAt() == null) {
            waterLog.setLoggedAt(LocalDateTime.now());
        }

        return waterLogRepository.save(waterLog);
    }

    public List<WaterLog> getTodayWaterLogs(String userId) {

        LocalDate today = LocalDate.now();

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        return waterLogRepository
                .findByUserIdAndLoggedAtBetween(
                        userId,
                        start,
                        end
                );
    }

    public double getTodayTotal(String userId) {

        return getTodayWaterLogs(userId)
                .stream()
                .mapToDouble(WaterLog::getAmountLiters)
                .sum();
    }

    public List<WaterLog> getAllWaterLogs(String userId) {

        return waterLogRepository
                .findByUserIdOrderByLoggedAtDesc(userId);
    }
}