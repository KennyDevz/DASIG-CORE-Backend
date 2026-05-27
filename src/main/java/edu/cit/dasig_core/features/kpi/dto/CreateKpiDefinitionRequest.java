package edu.cit.dasig_core.features.kpi.dto;

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

    @NotNull(message = "Threshold is required")
    private Double threshold;

    @NotNull(message = "Assigned Organization ID is required")
    private Long organizationId;
}