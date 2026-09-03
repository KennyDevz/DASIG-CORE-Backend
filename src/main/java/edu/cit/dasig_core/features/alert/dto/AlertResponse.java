package edu.cit.dasig_core.features.alert.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertResponse {
    private Long id;
    private Long submissionId;
    private Long kpiDefinitionId;
    private String alertType;
    private String severity;
    private String status;
    private LocalDateTime detectedAt;
}