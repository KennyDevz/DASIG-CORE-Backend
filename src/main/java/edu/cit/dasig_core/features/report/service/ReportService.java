package edu.cit.dasig_core.features.report.service;

import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.report.client.LLMApiClient;
import edu.cit.dasig_core.features.report.dto.ReportResponse;
import edu.cit.dasig_core.features.report.model.Report;
import edu.cit.dasig_core.features.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final KpiSubmissionRepository submissionRepository;
    private final ReportRepository reportRepository;
    private final LLMApiClient llmApiClient;

    public ReportResponse generateReport(Long organizationId, LocalDate periodFrom, LocalDate periodTo) {
        // 1. Fetch submissions for the organization
        List<KpiSubmission> submissions = submissionRepository
                .findByKpiDefinitionOrganizationId(organizationId);

        // 2. Filter by period
        List<KpiSubmission> filtered = submissions.stream()
                .filter(s -> {
                    LocalDate d = s.getSubmissionDate();
                    return (d.isEqual(periodFrom) || d.isAfter(periodFrom)) &&
                            (d.isEqual(periodTo) || d.isBefore(periodTo));
                })
                .toList();

        // 3. Build prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a performance analyst for a technology business incubator program. ");
        prompt.append("Generate a structured performance report based on the following KPI submission data.\n\n");
        prompt.append("Reporting Period: ").append(periodFrom).append(" to ").append(periodTo).append("\n\n");
        prompt.append("KPI Submissions:\n");

        if (filtered.isEmpty()) {
            prompt.append("No submissions found for this period.\n");
        } else {
            for (KpiSubmission s : filtered) {
                prompt.append(String.format(
                        "- KPI: %s | Target: %.2f %s | Submitted Value: %.2f | Achievement: %.1f%% | Status: %s | Period: %s\n",
                        s.getKpiDefinition().getName(),
                        s.getKpiDefinition().getTargetValue(),
                        s.getKpiDefinition().getUnit(),
                        s.getSubmittedValue(),
                        s.getAchievementRate(),
                        s.getPerformanceStatus(),
                        s.getReportingPeriod()
                ));
            }
        }

        prompt.append("\nWrite a professional report with these sections:\n");
        prompt.append("1. Overall Performance Summary\n");
        prompt.append("2. Underperforming KPIs\n");
        prompt.append("3. Major Progress Points\n");
        prompt.append("4. Recommendations\n");

        // 4. Call Groq
        String narrative;
        String status;
        try {
            narrative = llmApiClient.generateReport(prompt.toString());
            status = "GENERATED";
        } catch (Exception e) {
            narrative = "Report generation failed: " + e.getMessage();
            status = "FAILED";
        }

        // 5. Save report
        Report report = new Report();
        report.setOrganizationId(organizationId);
        report.setPeriodFrom(periodFrom);
        report.setPeriodTo(periodTo);
        report.setNarrativeText(narrative);
        report.setStatus(status);
        Report saved = reportRepository.save(report);

        return mapToResponse(saved);
    }

    public ReportResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));
        return mapToResponse(report);
    }

    public List<ReportResponse> getReportsByOrganization(Long organizationId) {
        return reportRepository.findByOrganizationIdOrderByGeneratedAtDesc(organizationId)
                .stream().map(this::mapToResponse).toList();
    }

    private ReportResponse mapToResponse(Report report) {
        ReportResponse response = new ReportResponse();
        response.setId(report.getId());
        response.setOrganizationId(report.getOrganizationId());
        response.setPeriodFrom(report.getPeriodFrom());
        response.setPeriodTo(report.getPeriodTo());
        response.setNarrativeText(report.getNarrativeText());
        response.setStatus(report.getStatus());
        response.setGeneratedAt(report.getGeneratedAt());
        return response;
    }
}