package edu.cit.dasig_core.features.dashboard.controller;

import edu.cit.dasig_core.features.dashboard.dto.DashboardResponse;
import edu.cit.dasig_core.features.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @PreAuthorize("hasAnyRole('DASIG_ADMIN', 'TBI_MANAGER', 'STAFF')")
    @GetMapping
    public ResponseEntity<DashboardResponse> getCurrentUserDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboardForCurrentUser());
    }
}
