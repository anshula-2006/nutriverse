package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.DashboardResponse;
import com.nutriverse.backend.service.DashboardService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(
            DashboardService dashboardService) {

        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestAttribute("authenticatedUserId") String userId) {

        DashboardResponse dashboard =
                dashboardService.getDashboard(userId);

        return ResponseEntity.ok(dashboard);
    }
}
