package edu.cit.dasig_core.features.kpi.controller;

import edu.cit.dasig_core.features.kpi.dto.CreateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.dto.KpiDefinitionResponse;
import edu.cit.dasig_core.features.kpi.dto.UpdateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.service.KpiDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kpi-definitions")
public class KpiDefinitionController {

    private final KpiDefinitionService kpiDefinitionService;

    public KpiDefinitionController(KpiDefinitionService kpiDefinitionService) {
        this.kpiDefinitionService = kpiDefinitionService;
    }

    // CREATE (Admin Only)
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    @PostMapping
    public ResponseEntity<KpiDefinitionResponse> createKpiDefinition(@Valid @RequestBody CreateKpiDefinitionRequest request) {
        KpiDefinitionResponse response = kpiDefinitionService.createKpiDefinition(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // UPDATE (Admin Only)
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<KpiDefinitionResponse> updateKpiDefinition(
            @PathVariable Long id,
            @Valid @RequestBody UpdateKpiDefinitionRequest request) {
        KpiDefinitionResponse response = kpiDefinitionService.updateKpiDefinition(id, request);
        return ResponseEntity.ok(response);
    }

    // DELETE (Admin Only)
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKpiDefinition(@PathVariable Long id) {
        kpiDefinitionService.deleteKpiDefinition(id);
        return ResponseEntity.noContent().build();
    }

    // GET ALL (Admin Only for global dashboard)
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    @GetMapping
    public ResponseEntity<List<KpiDefinitionResponse>> getAllKpiDefinitions() {
        List<KpiDefinitionResponse> responses = kpiDefinitionService.getAllKpiDefinitions();
        return ResponseEntity.ok(responses);
    }

    // GET BY ID (Viewable by Admin, Manager, and Staff)
    @PreAuthorize("hasAnyRole('DASIG_ADMIN', 'TBI_MANAGER', 'STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<KpiDefinitionResponse> getKpiDefinitionById(@PathVariable Long id) {
        KpiDefinitionResponse response = kpiDefinitionService.getKpiDefinitionById(id);
        return ResponseEntity.ok(response);
    }

    // GET BY ORGANIZATION (Viewable by Admin, Manager, and Staff)
    @PreAuthorize("hasAnyRole('DASIG_ADMIN', 'TBI_MANAGER', 'STAFF')")
    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<KpiDefinitionResponse>> getKpiDefinitionsByOrganizationId(@PathVariable Long organizationId) {
        List<KpiDefinitionResponse> responses = kpiDefinitionService.getKpiDefinitionsByOrganizationId(organizationId);
        return ResponseEntity.ok(responses);
    }
}