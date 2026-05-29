package edu.cit.dasig_core.features.report.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import java.time.format.DateTimeFormatter;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
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

    public ReportResponse generateReport(Long organizationId, LocalDate periodFrom, LocalDate periodTo) {
        // 1. Fetch submissions for the organization
        List<KpiSubmission> submissions = submissionRepository
                .findByOrganizationId(organizationId);

        // 2. Filter by period and official (TBI) submissions only
        List<KpiSubmission> filtered = submissions.stream()
                .filter(s -> {
                    LocalDate d = s.getSubmissionDate();
                    return s.getSubmissionType() == SubmissionType.FINAL
                            && (d.isEqual(periodFrom) || d.isAfter(periodFrom))
                            && (d.isEqual(periodTo) || d.isBefore(periodTo));
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