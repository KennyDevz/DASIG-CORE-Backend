package edu.cit.dasig_core.features.organization.controller;

import edu.cit.dasig_core.features.organization.dto.CreateOrganizationRequest;
import edu.cit.dasig_core.features.organization.dto.UpdateOrganizationRequest;
import edu.cit.dasig_core.features.organization.dto.OrganizationResponse;
import edu.cit.dasig_core.features.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    public ResponseEntity<OrganizationResponse> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse response = organizationService.createOrganization(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED); // 201 Created
    }

    @GetMapping
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    public ResponseEntity<List<OrganizationResponse>> getAllOrganizations() {
        List<OrganizationResponse> responses = organizationService.getAllOrganizations();
        return ResponseEntity.ok(responses); // 200 OK
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponse> getOrganizationById(@PathVariable Long id) {
        OrganizationResponse response = organizationService.getOrganizationById(id);
        return ResponseEntity.ok(response); // 200 OK
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable Long id, 
            @Valid @RequestBody UpdateOrganizationRequest request) {
        OrganizationResponse response = organizationService.updateOrganization(id, request);
        return ResponseEntity.ok(response); // 200 OK
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    public ResponseEntity<Void> deactivateOrganization(@PathVariable Long id) {
        organizationService.deactivateOrganization(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('DASIG_ADMIN')")
    public ResponseEntity<Void> activateOrganization(@PathVariable Long id) {
        organizationService.activateOrganization(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
