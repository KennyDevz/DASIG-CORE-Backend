package edu.cit.dasig_core.features.alert.controller;

import edu.cit.dasig_core.features.alert.dto.CommitteeOverdueAlertResponse;
import edu.cit.dasig_core.features.alert.dto.SubmitExplanationRequest;
import edu.cit.dasig_core.features.alert.service.OverdueExplanationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Committee Lead endpoints for Overdue alerts and their explanations (UC-4.4). */
@RestController
@RequestMapping("/api/committee-alerts")
@PreAuthorize("hasRole('TBI_MANAGER')")
@RequiredArgsConstructor
public class CommitteeAlertController {

    private final OverdueExplanationService overdueExplanationService;

    @GetMapping("/overdue")
    public ResponseEntity<List<CommitteeOverdueAlertResponse>> getOverdueAlerts() {
        return ResponseEntity.ok(overdueExplanationService.getOverdueAlertsForCommitteeLead());
    }

    @PostMapping("/{alertId}/explanation")
    public ResponseEntity<CommitteeOverdueAlertResponse> submitExplanation(
            @PathVariable Long alertId,
            @Valid @RequestBody SubmitExplanationRequest request) {
        return ResponseEntity.ok(overdueExplanationService.submitExplanation(alertId, request.getExplanation()));
    }
}
