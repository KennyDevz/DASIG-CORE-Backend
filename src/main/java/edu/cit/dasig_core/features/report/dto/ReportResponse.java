package edu.cit.dasig_core.features.report.dto;

import edu.cit.dasig_core.features.report.model.ReportType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ReportResponse {
    private String id;
    private Long committeeId;
    private String committeeName;
    private ReportType reportType;
    private Long kpiDefinitionId;
    private String kpiName;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String narrativeText;
    private String status;
    private LocalDateTime generatedAt;
}