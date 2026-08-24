package edu.cit.dasig_core.features.committee.controller;

import edu.cit.dasig_core.features.committee.dto.CreateCommitteeRequest;
import edu.cit.dasig_core.features.committee.dto.UpdateCommitteeRequest;
import edu.cit.dasig_core.features.committee.dto.CommitteeResponse;
import edu.cit.dasig_core.features.committee.service.CommitteeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/committees")
@PreAuthorize("hasRole('DASIG_ADMIN')")
public class CommitteeController {

    private final CommitteeService committeeService;

    public CommitteeController(CommitteeService committeeService) {
        this.committeeService = committeeService;
    }

    @PostMapping
    public ResponseEntity<CommitteeResponse> createCommittee(@Valid @RequestBody CreateCommitteeRequest request) {
        CommitteeResponse response = committeeService.createCommittee(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CommitteeResponse>> getAllCommittees() {
        List<CommitteeResponse> responses = committeeService.getAllCommittees();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommitteeResponse> getCommitteeById(@PathVariable Long id) {
        CommitteeResponse response = committeeService.getCommitteeById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommitteeResponse> updateCommittee(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommitteeRequest request) {
        CommitteeResponse response = committeeService.updateCommittee(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateCommittee(@PathVariable Long id) {
        committeeService.deactivateCommittee(id);
        return ResponseEntity.noContent().build();
    }
}
