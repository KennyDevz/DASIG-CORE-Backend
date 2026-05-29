package edu.cit.dasig_core.features.dashboard.dto;

import lombok.Data;

import java.util.List;

@Data
public class KpiPeriodHistoryItemResponse {
    private String reportingPeriod;
    private boolean current;
    private List<KpiPeriodSubmissionEntryResponse> submissions;
}
