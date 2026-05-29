package edu.cit.dasig_core.features.alert.dto;

import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AlertDetailResponse {
    private Long id;
    private Long submissionId;
    private String status;
    private LocalDateTime detectedAt;

    private Long kpiDefinitionId;
    private String kpiName;
    private Long organizationId;
    private String organizationName;

    private String reportingPeriod;

    // Updated to show the full math context
    private Double periodContribution; // Was previously submittedValue
    private Double cumulativeValue;    // NEW
    private Double scaledPeriodTarget; // NEW

    private LocalDate submissionDate;
    private Double achievementRate;
    private String performanceStatus;
    private SubmissionType submissionType;
}
