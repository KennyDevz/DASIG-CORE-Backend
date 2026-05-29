package edu.cit.dasig_core.features.report.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import java.time.format.DateTimeFormatter;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPeriodProgressCalculator;
import edu.cit.dasig_core.features.report.client.LLMApiClient;
import edu.cit.dasig_core.features.report.dto.ReportResponse;
import edu.cit.dasig_core.features.report.model.Report;
import edu.cit.dasig_core.features.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final KpiSubmissionRepository submissionRepository;
    private final ReportRepository reportRepository;
    private final LLMApiClient llmApiClient;
    private final KpiDefinitionRepository kpiDefinitionRepository;

    public ReportResponse generateOrganizationReport(Long organizationId, LocalDate periodFrom, LocalDate periodTo) {
        // Fetch all submissions for this organization (includes historical data for math)
        List<KpiSubmission> submissions = submissionRepository.findByOrganizationId(organizationId);
        String contextHeader = "Generate a comprehensive organizational performance report.\n\n";

        // Pass to the shared helper
        return buildAndSaveReport(submissions, organizationId, periodFrom, periodTo, contextHeader);
    }

    public ReportResponse generateKpiReport(Long kpiDefinitionId, LocalDate periodFrom, LocalDate periodTo) {
        // 1. Fetch the KPI to find out which organization owns it
        KpiDefinition kpi = kpiDefinitionRepository.findById(kpiDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("KPI not found with ID: " + kpiDefinitionId));

        // 2. Fetch submissions for this specific KPI
        List<KpiSubmission> submissions = submissionRepository.findByKpiDefinitionId(kpiDefinitionId);

        String contextHeader = "Generate a specific performance report focused strictly on the following single KPI for this incubator.\n\n";

        // 3. Safely pass the KPI's exact organizationId to the helper
        return buildAndSaveReport(submissions, kpi.getOrganization().getId(), periodFrom, periodTo, contextHeader);
    }

    public ReportResponse getReport(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));
        return mapToResponse(report);
    }

    public List<ReportResponse> getReportsByOrganization(Long organizationId) {
        return reportRepository.findByOrganizationIdOrderByGeneratedAtDesc(organizationId)
                .stream().map(this::mapToResponse).toList();
    }

    public byte[] exportAsPdf(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Fonts
            Font titleFont   = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Font headerFont  = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD);
            Font labelFont   = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
            Font bodyFont    = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
            Font bulletFont  = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);

            // Title
            Paragraph title = new Paragraph("Performance Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6);
            document.add(title);

            // Report ID
            Paragraph idParagraph = new Paragraph("Report No.: " + report.getId(), labelFont);
            idParagraph.setAlignment(Element.ALIGN_CENTER);
            idParagraph.setSpacingAfter(6);
            document.add(idParagraph);

            // Readable date format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
            String from = report.getPeriodFrom().format(formatter);
            String to   = report.getPeriodTo().format(formatter);

            Paragraph period = new Paragraph("Reporting Period: " + from + " — " + to, labelFont);
            period.setAlignment(Element.ALIGN_CENTER);
            period.setSpacingAfter(6);
            document.add(period);

            // Generated date
            DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a");
            Paragraph generated = new Paragraph("Generated: " + report.getGeneratedAt().format(dtFormatter), bodyFont);
            generated.setAlignment(Element.ALIGN_CENTER);
            generated.setSpacingAfter(20);
            document.add(generated);

            // Divider
            LineSeparator separator = new LineSeparator();
            separator.setLineColor(new BaseColor(200, 200, 200));
            document.add(new Chunk(separator));
            document.add(Chunk.NEWLINE);

            // Parse and render narrative
            String[] lines = report.getNarrativeText().split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    document.add(Chunk.NEWLINE);
                } else if (trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
                    // Section headers
                    String headerText = trimmed.replaceAll("^#{1,3}\\s*", "").replace("**", "");
                    Paragraph header = new Paragraph(headerText, headerFont);
                    header.setSpacingBefore(14);
                    header.setSpacingAfter(6);
                    document.add(header);
                } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    // Bullet points
                    String bulletText = trimmed.substring(2).replace("**", "");
                    Paragraph bullet = new Paragraph("• " + bulletText, bulletFont);
                    bullet.setIndentationLeft(16);
                    bullet.setSpacingAfter(4);
                    document.add(bullet);
                } else if (trimmed.startsWith("**") && trimmed.endsWith("**")) {
                    // Bold standalone lines
                    String boldText = trimmed.replace("**", "");
                    Paragraph bold = new Paragraph(boldText, labelFont);
                    bold.setSpacingAfter(4);
                    document.add(bold);
                } else {
                    // Regular paragraph — handle inline **bold**
                    Paragraph para = new Paragraph();
                    para.setSpacingAfter(4);
                    String[] parts = trimmed.split("\\*\\*");
                    for (int i = 0; i < parts.length; i++) {
                        if (i % 2 == 1) {
                            para.add(new Chunk(parts[i], labelFont)); // bold
                        } else {
                            para.add(new Chunk(parts[i], bodyFont));  // normal
                        }
                    }
                    document.add(para);
                }
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private ReportResponse buildAndSaveReport(
            List<KpiSubmission> allSubmissions,
            Long organizationId,
            LocalDate periodFrom,
            LocalDate periodTo,
            String contextHeader) {

        // Filter ONLY the official submissions that fall within the requested reporting window
        List<KpiSubmission> filteredWindowSubmissions = allSubmissions.stream()
                .filter(s -> {
                    LocalDate d = s.getSubmissionDate();
                    return s.getSubmissionType() == SubmissionType.FINAL
                            && (d.isEqual(periodFrom) || d.isAfter(periodFrom))
                            && (d.isEqual(periodTo) || d.isBefore(periodTo));
                })
                .toList();

        // Build Prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert performance analyst for a technology business incubator program.\n");
        prompt.append(contextHeader);

        prompt.append("=== SYSTEM LOGIC CONTEXT ===\n");
        prompt.append("- The system uses a cumulative progression model over the reporting timeline.\n");
        prompt.append("- 'Period Contribution' is the raw value achieved solely during that specific interval.\n");
        prompt.append("- 'Cumulative Value To Date' is the running total of all contributions up to that period.\n");
        prompt.append("- Performance Status (GREEN/ON_TRACK, YELLOW/AT_RISK, RED/DELAYED) and Achievement Rates are calculated strictly against the scaled cumulative targets and thresholds for that period, not the full annual target.\n");
        prompt.append("- These figures represent official FINAL entries approved by the TBI Manager.\n\n");

        prompt.append("=== REPORT PARAMETERS ===\n");
        prompt.append("Reporting Window: ").append(periodFrom).append(" to ").append(periodTo).append("\n\n");

        prompt.append("=== OFFICIAL KPI SUBMISSION RECORD ===\n");
        if (filteredWindowSubmissions.isEmpty()) {
            prompt.append("No official submissions found within this window.\n");
        } else {
            for (KpiSubmission s : filteredWindowSubmissions) {

                // Extract all historical FINAL submissions for this specific KPI to calculate accurate cumulative progress
                List<KpiSubmission> kpiHistory = allSubmissions.stream()
                        .filter(history -> history.getKpiDefinition().getId().equals(s.getKpiDefinition().getId())
                                && history.getSubmissionType() == SubmissionType.FINAL)
                        .toList();

                // Pass them into your exact utility class to get the true cumulative state
                KpiPeriodProgressCalculator.KpiPeriodProgress progress =
                        KpiPeriodProgressCalculator.calculateExisting(
                                s.getKpiDefinition(),
                                s.getReportingPeriod(),
                                kpiHistory
                        );

                // Inject the exact calculator outputs into the prompt
                prompt.append(String.format(
                        "- KPI Name: %s\n" +
                                "  * Incubator/Organization: %s\n" + // Added this so the LLM knows who submitted it in global reports
                                "  * Reporting Period: %s\n" +
                                "  * Frequency: %s\n" +
                                "  * Period Contribution (Raw): %.2f %s\n" +
                                "  * Cumulative Value To Date: %.2f %s\n" +
                                "  * Cumulative Achievement Rate: %.1f%%\n" +
                                "  * Current Status: %s\n" +
                                "  * Scaled Period Target: %.2f | Overall Global Target: %.2f\n" +
                                "  * Submission Date: %s\n" +
                                "  * Notes: %s\n\n",
                        s.getKpiDefinition().getName(),
                        s.getOrganization().getName(),
                        s.getReportingPeriod(),
                        s.getKpiDefinition().getReportingFrequency(),
                        s.getSubmittedValue(), s.getKpiDefinition().getUnit(),
                        progress.cumulativeSubmittedValue(), s.getKpiDefinition().getUnit(),
                        progress.achievementRate(),
                        progress.performanceStatus(),
                        progress.expectedTarget(),
                        s.getKpiDefinition().getTargetValue(),
                        s.getSubmissionDate(),
                        s.getNotes() != null ? s.getNotes() : "None"
                ));
            }
        }

        prompt.append("=== REQUIRED REPORT STRUCTURE ===\n");
        prompt.append("Write a professional narrative analyzing these trends using these exact headings:\n");
        prompt.append("1. Overall Performance Summary\n");
        prompt.append("   (Analyze how the cumulative trajectory is moving across the window. Appreciate steady gains even if temporary periods look low due to contribution dips.)\n");
        prompt.append("2. Underperforming KPIs\n");
        prompt.append("   (Highlight instances where the cumulative value fails to surpass the expected period thresholds, marking them as DELAYED or AT_RISK.)\n");
        prompt.append("3. Major Progress Points\n");
        prompt.append("   (Point out standout individual period contributions that significantly boosted or recovered the cumulative health status to ON_TRACK.)\n");
        prompt.append("4. Recommendations\n");
        prompt.append("   (Provide tactical recommendations for the incubator to maintain pace or correct courses to hit upcoming scaling milestones.)\n");

        // Call Groq LLM API
        String narrative;
        String status;
        try {
            narrative = llmApiClient.generateReport(prompt.toString());
            status = "GENERATED";
        } catch (Exception e) {
            narrative = "Report generation failed: " + e.getMessage();
            status = "FAILED";
        }

        // Save report (organizationId will safely be null for KPI global reports)
        Report report = new Report();
        report.setOrganizationId(organizationId);
        report.setPeriodFrom(periodFrom);
        report.setPeriodTo(periodTo);
        report.setNarrativeText(narrative);
        report.setStatus(status);

        Report saved = reportRepository.save(report);

        return mapToResponse(saved);
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