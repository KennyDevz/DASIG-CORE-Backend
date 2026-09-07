package edu.cit.dasig_core.features.report.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * One section of a structured report (heading + narrative body), with the
 * resolved KPI submissions that support it. Organization names and identifiers
 * here are always real — pseudonymization only ever applies to the outbound
 * LLM prompt, never to what's stored or returned to the frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportSectionDto {
    private String heading;
    private String text;
    private List<CitationDto> sources;
}
