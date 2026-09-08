package edu.cit.dasig_core.features.dashboard.dto;

import lombok.Data;

@Data
public class DashboardCommitteeOption {
    private Long id;
    private String name;
    private String organizationName;
    private boolean current;
    private boolean hasPendingSubmissions;
    private int pendingSubmissionsCount;
}