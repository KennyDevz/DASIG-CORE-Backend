package edu.cit.dasig_core.features.report.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ReportResponse {
    private Long id;
    private Long organizationId;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String narrativeText;
    private String status;
    private LocalDateTime generatedAt;
}