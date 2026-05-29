package edu.cit.dasig_core.features.report.controller;

import edu.cit.dasig_core.features.report.dto.GenerateKpiReportRequest;
import edu.cit.dasig_core.features.report.dto.GenerateOrgReportRequest;
import edu.cit.dasig_core.features.report.dto.ReportResponse;
import edu.cit.dasig_core.features.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('DASIG_ADMIN')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/generate/organization")
    public ResponseEntity<ReportResponse> generateByOrganization(@Valid @RequestBody GenerateOrgReportRequest request) {
        ReportResponse response = reportService.generateOrganizationReport(
                request.getOrganizationId(),
                request.getPeriodFrom(),
                request.getPeriodTo()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate/kpi")
    public ResponseEntity<ReportResponse> generateByKpi(@Valid @RequestBody GenerateKpiReportRequest request) {
        ReportResponse response = reportService.generateKpiReport(
                request.getKpiDefinitionId(),
                request.getPeriodFrom(),
                request.getPeriodTo()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getReport(@PathVariable String id) {
        return ResponseEntity.ok(reportService.getReport(id));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<ReportResponse>> getByOrganization(@PathVariable Long organizationId) {
        return ResponseEntity.ok(reportService.getReportsByOrganization(organizationId));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id) {
        byte[] pdf = reportService.exportAsPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}