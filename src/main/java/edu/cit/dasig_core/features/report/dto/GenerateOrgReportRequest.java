package edu.cit.dasig_core.features.report.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GenerateOrgReportRequest {

    @NotNull(message = "Organization ID is required")
    private Long organizationId;

    @NotNull(message = "Period from is required")
    private LocalDate periodFrom;

    @NotNull(message = "Period to is required")
    private LocalDate periodTo;
}