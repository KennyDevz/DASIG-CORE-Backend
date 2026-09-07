package edu.cit.dasig_core.features.report.dto;

import edu.cit.dasig_core.features.report.model.ReportType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    // Null/empty for reports generated before per-section citations were introduced —
    // the frontend falls back to rendering narrativeText as flat markdown in that case.
    private List<ReportSectionDto> sections;
}