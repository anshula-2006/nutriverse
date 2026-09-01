package com.nutriverse.backend.controller;

import com.nutriverse.backend.model.WaterLog;
import com.nutriverse.backend.service.WaterLogService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/water")
public class WaterLogController {

    private final WaterLogService waterLogService;

    public WaterLogController(WaterLogService waterLogService) {
        this.waterLogService = waterLogService;
    }

    @PostMapping
    public WaterLog addWater(
            @RequestBody WaterLog waterLog) {

        if (waterLog.getAmountLiters() == null
                || waterLog.getAmountLiters() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Water amount must be greater than 0"
            );
        }

        return waterLogService.addWater(waterLog);
    }

    @GetMapping("/today/{userId}")
    public List<WaterLog> getTodayWaterLogs(
            @PathVariable String userId) {

        return waterLogService
                .getTodayWaterLogs(userId);
    }

    @GetMapping("/today/{userId}/total")
    public double getTodayTotal(
            @PathVariable String userId) {

        return waterLogService
                .getTodayTotal(userId);
    }

    @GetMapping("/{userId}")
    public List<WaterLog> getAllWaterLogs(
            @PathVariable String userId) {

        return waterLogService
                .getAllWaterLogs(userId);
    }
}
