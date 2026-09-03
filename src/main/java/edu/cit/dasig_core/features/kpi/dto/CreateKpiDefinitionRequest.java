package edu.cit.dasig_core.features.kpi.dto;

import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateKpiDefinitionRequest {

    @NotBlank(message = "KPI name is required")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Target value is required")
    private Double targetValue;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotNull(message = "Deadline is required")
    private LocalDate deadline;

    /**
     * Minimum achievement threshold percentage. Defaults to 100 (full goal).
     * Optional — kept for backward compatibility; not used in status classification.
     */
    private Double threshold = 100.0;

    @NotNull(message = "Assigned Committee ID is required")
    private Long committeeId;

    /**
     * Reporting frequency. Defaults to ONE_TIME (Submit Anytime workflow).
     * Optional — admin does not need to configure this.
     */
    private ReportingFrequency reportingFrequency = ReportingFrequency.ONE_TIME;
}
