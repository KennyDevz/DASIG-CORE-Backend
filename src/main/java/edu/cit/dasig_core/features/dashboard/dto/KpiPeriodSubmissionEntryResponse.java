package edu.cit.dasig_core.features.dashboard.dto;

import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class KpiPeriodSubmissionEntryResponse {
    private Long id;
    private SubmissionType submissionType;
    private Double submittedValue;
    private Double achievementRate;
    private String performanceStatus;
    private String submittedByName;
    private String submittedByRole;
    private LocalDate submissionDate;
}
