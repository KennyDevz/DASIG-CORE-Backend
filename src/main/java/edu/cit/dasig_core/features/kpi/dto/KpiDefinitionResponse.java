package edu.cit.dasig_core.features.kpi.dto;

import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import lombok.Data;

import java.time.LocalDate;

@Data
public class KpiDefinitionResponse {
    private Long id;
    private String name;
    private String description;
    private Double targetValue;
    private String unit;
    private LocalDate deadline;
    private Double threshold;
    private Long organizationId;
    private String organizationName;
    private ReportingFrequency reportingFrequency;
}