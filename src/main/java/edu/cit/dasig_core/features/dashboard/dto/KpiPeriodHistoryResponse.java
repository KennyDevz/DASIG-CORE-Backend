package edu.cit.dasig_core.features.dashboard.dto;

import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class KpiPeriodHistoryResponse {
    private Long kpiDefinitionId;
    private String name;
    private String description;
    private Double targetValue;
    private String unit;
    private LocalDate deadline;
    private ReportingFrequency reportingFrequency;
    private String currentPeriod;
    private String organization;
    private List<KpiPeriodHistoryItemResponse> periods;
}
