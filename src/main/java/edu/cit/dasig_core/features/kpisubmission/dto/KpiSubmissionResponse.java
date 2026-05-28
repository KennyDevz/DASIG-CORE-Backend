package edu.cit.dasig_core.features.kpisubmission.dto;

import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class KpiSubmissionResponse {
    private Long id;
    private Long kpiDefinitionId;
    private String kpiName;
    private String reportingPeriod;
    private Double submittedValue;
    private LocalDate submissionDate;
    private String notes;
    private SubmissionType submissionType;
    private Double achievementRate;
    private String performanceStatus;
    private List<SubmissionDocumentResponse> documents;
    private LocalDateTime createdAt;
}
