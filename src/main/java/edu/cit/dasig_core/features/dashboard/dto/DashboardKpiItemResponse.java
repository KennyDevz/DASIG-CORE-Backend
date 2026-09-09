package edu.cit.dasig_core.features.dashboard.dto;

import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DashboardKpiItemResponse {
    private Long id;
    private String name;
    private String description;
    private Double targetValue;
    private Double overallTargetValue;
    private Double periodTargetValue;
    private Double submittedValue;
    private String unit;
    private LocalDate deadline;
    private String organization;
    private Double achievementRate;
    private String status;
    private ReportingFrequency reportingFrequency;
    private String reportingPeriod;
    private String kpiStatus;
    private boolean archived;
    private Long committeeId;
    private String committeeName;
    private List<DashboardOrganizationProgressResponse> organizationBreakdowns;
}
