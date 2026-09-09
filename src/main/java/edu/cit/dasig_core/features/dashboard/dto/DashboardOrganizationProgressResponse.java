package edu.cit.dasig_core.features.dashboard.dto;

import lombok.Data;

@Data
public class DashboardOrganizationProgressResponse {
    private Long organizationId;
    private String organizationName;
    private Double submittedValue;
    private Double achievementRate;
    private String status;
}
