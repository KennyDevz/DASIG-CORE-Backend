package edu.cit.dasig_core.features.report.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

/**
 * A resolved source citation shown to an authenticated DASIG user.
 * Always built after resolving the LLM's ephemeral record tag back to a real
 * KpiSubmission — never carries the tag itself, and never crosses back out to the LLM.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitationDto {
    private Long submissionId;
    private String submissionReference;
    private String kpiName;
    private String organizationName;
    private Double submittedValue;
    private Double targetValue;
    private LocalDate submissionDate;
}
