package edu.cit.dasig_core.features.kpisubmission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateKpiSubmissionRequest {

    @NotNull(message = "KPI definition ID is required")
    private Long kpiDefinitionId;

    @NotBlank(message = "Reporting period is required")
    private String reportingPeriod;

    @NotNull(message = "Submitted value is required")
    private Double submittedValue;

    @NotNull(message = "Submission date is required")
    private LocalDate submissionDate;

    private String notes;
}
