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
    private String committeeName;

    private String alertType;
    private String severity;
    private LocalDate deadline;
    private Long daysUntilDeadline;

    private String reportingPeriod;

    private Double periodContribution;
    private Double cumulativeValue;
    private Double scaledPeriodTarget;

    private LocalDate submissionDate;
    private Double achievementRate;
    private String performanceStatus;
    private SubmissionType submissionType;
}