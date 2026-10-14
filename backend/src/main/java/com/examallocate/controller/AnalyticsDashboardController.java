package com.examallocate.controller;

import com.examallocate.config.AdminGuard;
import com.examallocate.service.AnalyticsDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsDashboardController {
    private final AnalyticsDashboardService dashboardService;
    private final AdminGuard adminGuard;

    /** Current database-backed overview for the admin dashboard. */
    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        adminGuard.check(key);
        return dashboardService.overview();
    }
}
