package edu.cit.dasig_core.features.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DashboardKpiItemResponse {
    private Long id;
    private String name;
    private String description;
    private Double targetValue;
    private Double submittedValue;
    private String unit;
    private LocalDate deadline;
    private String organization;
    private Double achievementRate;
    private String status;
}
