package edu.cit.dasig_core.features.kpisubmission.controller;

import edu.cit.dasig_core.features.kpi.dto.KpiDefinitionResponse;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpisubmission.dto.CreateKpiSubmissionRequest;
import edu.cit.dasig_core.features.kpisubmission.dto.KpiSubmissionResponse;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.service.KpiSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/kpi-submissions")
public class KpiSubmissionController {

    private final KpiSubmissionService kpiSubmissionService;

    public KpiSubmissionController(KpiSubmissionService kpiSubmissionService) {
        this.kpiSubmissionService = kpiSubmissionService;
    }

    @PreAuthorize("hasAnyRole('TBI_MANAGER', 'STAFF')")
    @GetMapping
    public ResponseEntity<List<KpiSubmissionResponse>> getSubmissions(
            @RequestParam(required = false) Long kpiDefinitionId,
            @RequestParam(required = false) String reportingPeriod,
            @RequestParam(required = false) SubmissionType submissionType
    ) {
        List<KpiSubmissionResponse> responses = kpiSubmissionService.getSubmissionsForCurrentUser(
                kpiDefinitionId,
                reportingPeriod,
                submissionType
        );
        return ResponseEntity.ok(responses);
    }

    @PreAuthorize("hasAnyRole('TBI_MANAGER', 'STAFF')")
    @GetMapping("/assignable")
    public ResponseEntity<List<KpiDefinitionResponse>> getAssignableKpis() {
        List<KpiDefinitionResponse> responses = kpiSubmissionService.getAssignedKpisForCurrentUser()
                .stream()
                .map(this::toKpiDefinitionResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PreAuthorize("hasAnyRole('TBI_MANAGER', 'STAFF')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KpiSubmissionResponse> createSubmission(
            @Valid @RequestPart("request") CreateKpiSubmissionRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        List<MultipartFile> uploadedFiles = files != null ? files : Collections.emptyList();
        KpiSubmissionResponse response = kpiSubmissionService.createSubmission(request, uploadedFiles);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    private KpiDefinitionResponse toKpiDefinitionResponse(KpiDefinition kpiDefinition) {
        KpiDefinitionResponse response = new KpiDefinitionResponse();
        response.setId(kpiDefinition.getId());
        response.setName(kpiDefinition.getName());
        response.setDescription(kpiDefinition.getDescription());
        response.setTargetValue(kpiDefinition.getTargetValue());
        response.setUnit(kpiDefinition.getUnit());
        response.setDeadline(kpiDefinition.getDeadline());
        response.setThreshold(kpiDefinition.getThreshold());
        response.setOrganizationId(kpiDefinition.getOrganization().getId());
        response.setOrganizationName(kpiDefinition.getOrganization().getName());
        return response;
    }
}
