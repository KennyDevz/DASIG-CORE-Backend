package edu.cit.dasig_core.features.dashboard.dto;

import lombok.Data;

import java.util.List;

@Data
public class DashboardResponse {
    private String role;
    private Long organizationId;
    private String organizationName;
    private List<DashboardKpiItemResponse> kpis;
}
