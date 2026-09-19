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
    public WaterLogController(WaterLogService waterLogService) { this.waterLogService = waterLogService; }
    public record WaterRequest(Double amountLiters) {}

    @PostMapping
    public WaterLog addWater(@RequestAttribute("authenticatedUserId") String userId,
                             @RequestBody WaterRequest request) {
        Double amount = request.amountLiters();
        if (amount == null || !Double.isFinite(amount) || amount <= 0 || amount > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Water amount must be greater than 0 and at most 10 liters per entry");
        }
        return waterLogService.addWater(new WaterLog(userId, amount));
    }

    @GetMapping("/today")
    public List<WaterLog> getTodayWaterLogs(@RequestAttribute("authenticatedUserId") String userId) {
        return waterLogService.getTodayWaterLogs(userId);
    }

    @GetMapping("/today/total")
    public double getTodayTotal(@RequestAttribute("authenticatedUserId") String userId) {
        return waterLogService.getTodayTotal(userId);
    }

    @GetMapping
    public List<WaterLog> getAllWaterLogs(@RequestAttribute("authenticatedUserId") String userId) {
        return waterLogService.getAllWaterLogs(userId);
    }
}
