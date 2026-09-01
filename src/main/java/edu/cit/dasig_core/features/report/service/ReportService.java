package edu.cit.dasig_core.features.report.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import java.time.format.DateTimeFormatter;

import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPeriodProgressCalculator;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.report.client.LLMApiClient;
import edu.cit.dasig_core.features.report.dto.ReportResponse;
import edu.cit.dasig_core.features.report.model.Report;
import edu.cit.dasig_core.features.report.model.ReportType;
import edu.cit.dasig_core.features.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Pattern INLINE_FORMAT_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*");

    private final KpiSubmissionRepository submissionRepository;
    private final ReportRepository reportRepository;
    private final LLMApiClient llmApiClient;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final CommitteeRepository committeeRepository;

    public ReportResponse generateCommitteeReport(Long committeeId, LocalDate periodFrom, LocalDate periodTo) {
        Committee committee = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Committee not found with ID: " + committeeId));

        // A committee report aggregates submissions across every organization under that committee
        List<Long> organizationIds = committee.getOrganizations().stream()
                .map(Organization::getId)
                .toList();
        List<KpiSubmission> submissions = organizationIds.isEmpty()
                ? List.of()
                : submissionRepository.findByOrganizationIdIn(organizationIds);

        String contextHeader = "Generate a comprehensive committee performance report covering all incubator "
                + "organizations under the \"" + committee.getName() + "\" committee.\n\n";

        return buildAndSaveReport(submissions, committeeId, ReportType.COMMITTEE, null, periodFrom, periodTo, contextHeader);
    }

    public ReportResponse generateKpiReport(Long kpiDefinitionId, LocalDate periodFrom, LocalDate periodTo) {
        // 1. Fetch the KPI to find out which committee owns it
        KpiDefinition kpi = kpiDefinitionRepository.findById(kpiDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("KPI not found with ID: " + kpiDefinitionId));

        // 2. Fetch submissions for this specific KPI (across every organization under the committee that reports on it)
        List<KpiSubmission> submissions = submissionRepository.findByKpiDefinitionId(kpiDefinitionId);

        String contextHeader = "Generate a specific performance report focused strictly on the following single KPI, "
                + "covering all incubator organizations under the \"" + kpi.getCommittee().getName() + "\" committee that report on it.\n\n";

        return buildAndSaveReport(submissions, kpi.getCommittee().getId(), ReportType.KPI, kpiDefinitionId, periodFrom, periodTo, contextHeader);
    }

    public ReportResponse getReport(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));
        return mapToResponse(report);
    }

    public List<ReportResponse> getAllReports() {
        return reportRepository.findAllByOrderByGeneratedAtDesc()
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
            Font italicFont  = new Font(Font.FontFamily.HELVETICA, 10, Font.ITALIC);
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
            int i = 0;
            while (i < lines.length) {
                String trimmed = lines[i].trim();

                if (trimmed.isEmpty()) {
                    document.add(Chunk.NEWLINE);
                    i++;
                } else if (trimmed.startsWith("|")) {
                    // Markdown table. The LLM doesn't always emit a clean |---|---| separator row,
                    // and sometimes wraps a single logical row across multiple physical lines mid-cell,
                    // so join lines into logical rows until each one actually ends with '|'.
                    List<String> rawRows = new ArrayList<>();
                    StringBuilder rowBuffer = new StringBuilder();
                    int continuationCount = 0;
                    while (i < lines.length) {
                        String candidate = lines[i].trim();
                        if (rowBuffer.length() == 0) {
                            if (candidate.isEmpty() || !candidate.startsWith("|")) {
                                break; // left the table
                            }
                            rowBuffer.append(candidate);
                            continuationCount = 0;
                        } else {
                            if (candidate.isEmpty()) {
                                break; // unterminated row; stop rather than swallow the rest of the document
                            }
                            rowBuffer.append(' ').append(candidate);
                            continuationCount++;
                        }
                        i++;
                        if (rowBuffer.toString().endsWith("|") || continuationCount >= 6) {
                            rawRows.add(rowBuffer.toString());
                            rowBuffer.setLength(0);
                        }
                    }
                    if (rowBuffer.length() > 0) {
                        rawRows.add(rowBuffer.toString());
                    }

                    List<String> headerCells = splitTableRow(rawRows.get(0));
                    int bodyStart = (rawRows.size() > 1 && isTableSeparatorRow(rawRows.get(1))) ? 2 : 1;
                    List<List<String>> bodyRows = new ArrayList<>();
                    for (int r = bodyStart; r < rawRows.size(); r++) {
                        bodyRows.add(splitTableRow(rawRows.get(r)));
                    }
                    document.add(buildTable(headerCells, bodyRows, labelFont, bodyFont, italicFont));
                } else if (trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
                    // Markdown section headers
                    String headerText = stripInlineMarkers(trimmed.replaceAll("^#{1,3}\\s*", ""));
                    Paragraph header = new Paragraph(headerText, headerFont);
                    header.setSpacingBefore(14);
                    header.setSpacingAfter(6);
                    document.add(header);
                    i++;
                } else if (trimmed.matches("^\\d+\\.\\s+[^.!?]+$")) {
                    // Numbered section headings (e.g. "1. Overall Performance Summary"), per the required report structure
                    String headerText = stripInlineMarkers(trimmed.replaceFirst("^\\d+\\.\\s+", ""));
                    Paragraph header = new Paragraph(headerText, headerFont);
                    header.setSpacingBefore(14);
                    header.setSpacingAfter(6);
                    document.add(header);
                    i++;
                } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    // Bullet points — handle inline **bold**/*italic*
                    Paragraph bullet = new Paragraph();
                    bullet.add(new Chunk("• ", bulletFont));
                    appendInlineFormatted(bullet, trimmed.substring(2), bulletFont, labelFont, italicFont);
                    bullet.setIndentationLeft(16);
                    bullet.setSpacingAfter(4);
                    document.add(bullet);
                    i++;
                } else {
                    // Regular paragraph — handle inline **bold**/*italic*
                    Paragraph para = new Paragraph();
                    para.setSpacingAfter(4);
                    appendInlineFormatted(para, trimmed, bodyFont, labelFont, italicFont);
                    document.add(para);
                    i++;
                }
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private boolean isTableRow(String line) {
        return line.length() > 1 && line.startsWith("|") && line.endsWith("|");
    }

    private boolean isTableSeparatorRow(String line) {
        return isTableRow(line) && line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ');
    }

    private List<String> splitTableRow(String line) {
        String[] rawCells = line.substring(1, line.length() - 1).split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String cell : rawCells) {
            cells.add(cell.trim());
        }
        return cells;
    }

    private PdfPTable buildTable(
            List<String> headerCells, List<List<String>> bodyRows, Font headerFont, Font bodyFont, Font italicFont) {
        PdfPTable table = new PdfPTable(headerCells.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(10);

        for (String header : headerCells) {
            Phrase phrase = new Phrase();
            appendInlineFormatted(phrase, header, headerFont, headerFont, italicFont);
            PdfPCell cell = new PdfPCell(phrase);
            cell.setBackgroundColor(new BaseColor(230, 230, 230));
            cell.setPadding(5);
            table.addCell(cell);
        }

        for (List<String> row : bodyRows) {
            for (int c = 0; c < headerCells.size(); c++) {
                String value = c < row.size() ? row.get(c) : "";
                Phrase phrase = new Phrase();
                appendInlineFormatted(phrase, value, bodyFont, headerFont, italicFont);
                PdfPCell cell = new PdfPCell(phrase);
                cell.setPadding(5);
                table.addCell(cell);
            }
        }

        return table;
    }

    /**
     * Appends text to a Phrase (or Paragraph, which extends Phrase), splitting out
     * **bold** and *italic* markdown spans into their own Chunks with the matching font.
     */
    private void appendInlineFormatted(Phrase target, String text, Font normalFont, Font boldFont, Font italicFont) {
        Matcher matcher = INLINE_FORMAT_PATTERN.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                target.add(new Chunk(text.substring(lastEnd, matcher.start()), normalFont));
            }
            if (matcher.group(1) != null) {
                target.add(new Chunk(matcher.group(1), boldFont));
            } else {
                target.add(new Chunk(matcher.group(2), italicFont));
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            target.add(new Chunk(text.substring(lastEnd), normalFont));
        }
    }

    /** Strips **bold**//**italic* markdown markers from text without preserving styling (e.g. for headers). */
    private String stripInlineMarkers(String text) {
        Matcher matcher = INLINE_FORMAT_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            result.append(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private ReportResponse buildAndSaveReport(
            List<KpiSubmission> allSubmissions,
            Long committeeId,
            ReportType reportType,
            Long kpiDefinitionId,
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
        prompt.append("- These figures represent official FINAL entries approved by the Committee Lead.\n\n");

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
                                "  * Incubator/Committee: %s\n" + // Added this so the LLM knows who submitted it in global reports
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
        prompt.append("Write a professional narrative analyzing these trends using these exact headings, and nothing else:\n");
        prompt.append("1. Overall Performance Summary\n");
        prompt.append("   (Analyze how the cumulative trajectory is moving across the window. Appreciate steady gains even if temporary periods look low due to contribution dips.)\n");
        prompt.append("2. Underperforming KPIs\n");
        prompt.append("   (Highlight instances where the cumulative value fails to surpass the expected period thresholds, marking them as DELAYED or AT_RISK.)\n");
        prompt.append("3. Major Progress Points\n");
        prompt.append("   (Point out standout individual period contributions that significantly boosted or recovered the cumulative health status to ON_TRACK.)\n");
        prompt.append("4. Recommendations\n");
        prompt.append("   (Provide tactical recommendations for the incubator to maintain pace or correct courses to hit upcoming scaling milestones.)\n\n");

        prompt.append("=== OUTPUT FORMATTING RULES (STRICT) ===\n");
        prompt.append("- Do NOT include a title, subtitle, or preamble before heading \"1.\" — the response must start directly with \"1. Overall Performance Summary\".\n");
        prompt.append("- Do NOT use horizontal rule lines (e.g. \"---\" or \"***\") anywhere.\n");
        prompt.append("- Do NOT escape asterisks with a backslash (never write \"\\*\"); if a line should start with a literal \"-\" or \"*\" character that is not a bullet or emphasis, rephrase it instead.\n");
        prompt.append("- For any tabular or columnar data (per-KPI breakdowns, comparisons, etc.), you MUST use a proper Markdown pipe table: a header row, then a \"|---|---|\" separator row, then data rows — never align columns with plain spaces.\n");
        prompt.append("- Use **bold** only to emphasize a handful of key terms or figures, and plain text otherwise. Avoid *italics* unless truly necessary. Never nest bold and italics together.\n");
        prompt.append("- Keep every hyphen in compound words (e.g. \"period-specific\", \"real-time\") and in dates (e.g. \"2026-08-01\") exactly as written — do not drop them.\n");

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

        Report report = new Report();
        report.setCommitteeId(committeeId);
        report.setReportType(reportType);
        report.setKpiDefinitionId(kpiDefinitionId);
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
        response.setCommitteeId(report.getCommitteeId());
        response.setCommitteeName(committeeRepository.findById(report.getCommitteeId())
                .map(Committee::getName)
                .orElse(null));
        response.setReportType(report.getReportType());
        response.setKpiDefinitionId(report.getKpiDefinitionId());
        if (report.getKpiDefinitionId() != null) {
            response.setKpiName(kpiDefinitionRepository.findById(report.getKpiDefinitionId())
                    .map(KpiDefinition::getName)
                    .orElse(null));
        }
        response.setPeriodFrom(report.getPeriodFrom());
        response.setPeriodTo(report.getPeriodTo());
        response.setNarrativeText(report.getNarrativeText());
        response.setStatus(report.getStatus());
        response.setGeneratedAt(report.getGeneratedAt());
        return response;
    }
}