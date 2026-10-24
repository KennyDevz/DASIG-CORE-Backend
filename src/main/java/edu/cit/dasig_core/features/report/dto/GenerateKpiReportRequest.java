package edu.cit.dasig_core.features.report.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateKpiReportRequest {
    @NotNull(message = "KPI Definition ID is required")
    private Long kpiDefinitionId;
}