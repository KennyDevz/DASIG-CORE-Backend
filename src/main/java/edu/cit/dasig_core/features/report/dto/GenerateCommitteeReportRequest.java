package edu.cit.dasig_core.features.report.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateCommitteeReportRequest {

    @NotNull(message = "Committee ID is required")
    private Long committeeId;
}