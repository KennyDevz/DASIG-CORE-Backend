package edu.cit.dasig_core.features.dashboard.controller;

import edu.cit.dasig_core.features.dashboard.dto.DashboardResponse;
import edu.cit.dasig_core.features.dashboard.dto.KpiPeriodHistoryResponse;
import edu.cit.dasig_core.features.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @PreAuthorize("hasAnyRole('DASIG_ADMIN', 'TBI_MANAGER', 'STAFF')")
    @GetMapping
    public ResponseEntity<DashboardResponse> getCurrentUserDashboard(
            @RequestParam(required = false) String reportingPeriod
    ) {
        return ResponseEntity.ok(dashboardService.getDashboardForCurrentUser(reportingPeriod));
    }

    @GetMapping("/kpis/{kpiDefinitionId}/period-history")
    public ResponseEntity<KpiPeriodHistoryResponse> getKpiPeriodHistory(
            @PathVariable Long kpiDefinitionId
    ) {
        return ResponseEntity.ok(dashboardService.getKpiPeriodHistory(kpiDefinitionId));
    }
}
