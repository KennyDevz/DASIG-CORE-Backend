package edu.cit.dasig_core.features.alert.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An Overdue alert as seen by the Committee Lead, with its explanation status (UC-4.4). */
@Data
public class CommitteeOverdueAlertResponse {
    private Long alertId;
    private Long kpiDefinitionId;
    private String kpiName;
    private String committeeName;
    private LocalDate deadline;
    private String alertStatus;
    private LocalDateTime detectedAt;

    private String explanationStatus;
    private String explanation;
    private LocalDateTime explainedAt;
}
