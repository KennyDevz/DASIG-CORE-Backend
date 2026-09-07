package edu.cit.dasig_core.features.report.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import edu.cit.dasig_core.features.report.dto.CitationDto;
import edu.cit.dasig_core.features.report.dto.ReportResponse;
import edu.cit.dasig_core.features.report.dto.ReportSectionDto;
import edu.cit.dasig_core.features.report.model.Report;
import edu.cit.dasig_core.features.report.model.ReportType;
import edu.cit.dasig_core.features.report.repository.ReportRepository;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Pattern INLINE_FORMAT_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*");
    // Single source of truth for human-readable dates anywhere in a report — the prompt (so the LLM
    // sees this style to begin with), the narrative-date cleanup pass, and the PDF export.
    private static final DateTimeFormatter REPORT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    private final KpiSubmissionRepository submissionRepository;
    private final ReportRepository reportRepository;
    private final LLMApiClient llmApiClient;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final CommitteeRepository committeeRepository;
    private final ObjectMapper objectMapper;

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
            DateTimeFormatter formatter = REPORT_DATE_FORMAT;
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

            // Structured (post-citations) reports render section-by-section, each followed by a short
            // "Sources: 1, 3" reference line, then one consolidated numbered "Sources" list at the end —
            // the same submission is often cited by several sections, so its full detail is only
            // printed once, in that final list, rather than repeated under every section that cites it.
            List<ReportSectionDto> sections = deserializeSections(report.getSectionsJson());
            if (sections != null && !sections.isEmpty()) {
                Map<Long, CitationDto> uniqueCitations = new LinkedHashMap<>();
                for (ReportSectionDto section : sections) {
                    if (section.getSources() == null) {
                        continue;
                    }
                    for (CitationDto citation : section.getSources()) {
                        uniqueCitations.putIfAbsent(citation.getSubmissionId(), citation);
                    }
                }
                List<CitationDto> orderedCitations = new ArrayList<>(uniqueCitations.values());
                Map<Long, Integer> citationNumbers = new LinkedHashMap<>();
                for (int idx = 0; idx < orderedCitations.size(); idx++) {
                    citationNumbers.put(orderedCitations.get(idx).getSubmissionId(), idx + 1);
                }

                for (ReportSectionDto section : sections) {
                    Paragraph sectionHeader = new Paragraph(stripInlineMarkers(section.getHeading()), headerFont);
                    sectionHeader.setSpacingBefore(14);
                    sectionHeader.setSpacingAfter(6);
                    document.add(sectionHeader);

                    List<Integer> numbersForSection = section.getSources() == null || section.getSources().isEmpty()
                            ? List.of()
                            : section.getSources().stream()
                                    .map(c -> citationNumbers.get(c.getSubmissionId()))
                                    .distinct()
                                    .sorted()
                                    .toList();
                    renderNarrativeBody(
                            document, section.getText(), headerFont, labelFont, bodyFont, italicFont, bulletFont,
                            numbersForSection);
                }

                if (!orderedCitations.isEmpty()) {
                    Paragraph sourcesHeader = new Paragraph("Sources", headerFont);
                    sourcesHeader.setSpacingBefore(18);
                    sourcesHeader.setSpacingAfter(6);
                    document.add(sourcesHeader);

                    for (int idx = 0; idx < orderedCitations.size(); idx++) {
                        CitationDto c = orderedCitations.get(idx);
                        String line = (idx + 1) + ". " + c.getKpiName() + " (" + c.getOrganizationName() + ") — Submission #"
                                + c.getSubmissionId() + ", " + c.getSubmissionDate().format(formatter);
                        Paragraph sourceLine = new Paragraph(line, italicFont);
                        sourceLine.setSpacingAfter(4);
                        document.add(sourceLine);
                    }
                }
            } else {
                renderNarrativeBody(
                        document, report.getNarrativeText(), headerFont, labelFont, bodyFont, italicFont, bulletFont,
                        List.of());
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private List<ReportSectionDto> deserializeSections(String sectionsJson) {
        if (sectionsJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(sectionsJson, new TypeReference<List<ReportSectionDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize sectionsJson, falling back to flat narrative: {}", e.getMessage());
            return null;
        }
    }

    private static final BaseColor CITATION_REF_COLOR = new BaseColor(29, 78, 216); // matches the on-screen chip blue

    /**
     * Appends small superscript-style "[1,3]" reference numbers to a Phrase/Paragraph — the PDF
     * analogue of the on-screen numbered badges, pointing into the consolidated "Sources" list.
     */
    private void appendTrailingRefNumbers(Phrase target, List<Integer> numbers) {
        Font refFont = new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD, CITATION_REF_COLOR);
        String label = numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
        Chunk refChunk = new Chunk(" [" + label + "]", refFont);
        refChunk.setTextRise(4f);
        target.add(refChunk);
    }

    /**
     * Renders one narrative body (markdown-ish text: headings/tables/bullets/paragraphs) into the
     * PDF. When {@code trailingRefNumbers} is non-empty, it's appended inline to the end of the
     * last content line if that line is a plain paragraph (the common case for these sections) —
     * otherwise (the text ends in a table/heading/bullet instead) it's added as its own compact
     * line right after, mirroring the on-screen fallback for the same situation.
     */
    private void renderNarrativeBody(
            Document document, String text, Font headerFont, Font labelFont, Font bodyFont, Font italicFont, Font bulletFont,
            List<Integer> trailingRefNumbers)
            throws DocumentException {
            String[] lines = text.split("\n");

            int lastContentLineIndex = -1;
            for (int idx = lines.length - 1; idx >= 0; idx--) {
                if (!lines[idx].trim().isEmpty()) {
                    lastContentLineIndex = idx;
                    break;
                }
            }
            boolean hasTrailingRefs = trailingRefNumbers != null && !trailingRefNumbers.isEmpty();
            boolean trailingAppended = false;

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
                    if (i == lastContentLineIndex && hasTrailingRefs) {
                        appendTrailingRefNumbers(para, trailingRefNumbers);
                        trailingAppended = true;
                    }
                    document.add(para);
                    i++;
                }
            }

            // The text ended in a table/heading/bullet rather than a plain paragraph — there was no
            // line to append the reference numbers to, so add them as their own compact line instead.
            if (hasTrailingRefs && !trailingAppended) {
                Paragraph fallback = new Paragraph();
                appendTrailingRefNumbers(fallback, trailingRefNumbers);
                document.add(fallback);
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

        // Every organization/submission identity sent to the third-party LLM is pseudonymized per the
        // Data Minimization Policy: organizations become "Org-<id>" (the id is already a non-identifying
        // opaque value, so no separate persisted mapping is needed) and submissions get an ephemeral
        // "REC-N" tag scoped only to this single report generation. Both are resolved back to real
        // values below, after the LLM responds — the LLM never sees a real org name, and no field
        // outside the permitted list (e.g. Notes) is ever included.
        Map<String, KpiSubmission> tagToSubmission = new LinkedHashMap<>();
        Map<Long, String> orgIdToName = new LinkedHashMap<>();

        prompt.append("=== OFFICIAL KPI SUBMISSION RECORD ===\n");
        if (filteredWindowSubmissions.isEmpty()) {
            prompt.append("No official submissions found within this window.\n");
        } else {
            int tagCounter = 1;
            for (KpiSubmission s : filteredWindowSubmissions) {
                String recordTag = "REC-" + (tagCounter++);
                tagToSubmission.put(recordTag, s);
                orgIdToName.putIfAbsent(s.getOrganization().getId(), s.getOrganization().getName());

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

                // Inject the exact calculator outputs into the prompt — organization identity is a
                // pseudonymous "Org-N" code only; Notes is never included (see class-level note above).
                prompt.append(String.format(
                        "- Record Tag: %s\n" +
                                "  * KPI Name: %s\n" +
                                "  * Organization Reference: Org-%d\n" +
                                "  * Reporting Period: %s\n" +
                                "  * Frequency: %s\n" +
                                "  * Period Contribution (Raw): %.2f %s\n" +
                                "  * Cumulative Value To Date: %.2f %s\n" +
                                "  * Cumulative Achievement Rate: %.1f%%\n" +
                                "  * Current Status: %s\n" +
                                "  * Scaled Period Target: %.2f | Overall Global Target: %.2f\n" +
                                "  * Submission Date: %s\n\n",
                        recordTag,
                        s.getKpiDefinition().getName(),
                        s.getOrganization().getId(),
                        s.getReportingPeriod(),
                        s.getKpiDefinition().getReportingFrequency(),
                        s.getSubmittedValue(), s.getKpiDefinition().getUnit(),
                        progress.cumulativeSubmittedValue(), s.getKpiDefinition().getUnit(),
                        progress.achievementRate(),
                        progress.performanceStatus(),
                        progress.expectedTarget(),
                        s.getKpiDefinition().getTargetValue(),
                        s.getSubmissionDate().format(REPORT_DATE_FORMAT)
                ));
            }
        }

        prompt.append("=== REQUIRED OUTPUT FORMAT (STRICT JSON) ===\n");
        prompt.append("Respond with a JSON object containing a \"sections\" array of exactly 4 objects, in this exact order, ");
        prompt.append("each with a \"heading\", a \"text\" (the narrative body), and a \"citedRecordTags\" array:\n");
        prompt.append("1. heading: \"Overall Performance Summary\" — analyze how the cumulative trajectory is moving across the window. Appreciate steady gains even if temporary periods look low due to contribution dips.\n");
        prompt.append("2. heading: \"Underperforming KPIs\" — highlight instances where the cumulative value fails to surpass the expected period thresholds, marking them as DELAYED or AT_RISK.\n");
        prompt.append("3. heading: \"Major Progress Points\" — point out standout individual period contributions that significantly boosted or recovered the cumulative health status to ON_TRACK.\n");
        prompt.append("4. heading: \"Recommendations\" — provide tactical recommendations for the incubator to maintain pace or correct courses to hit upcoming scaling milestones.\n\n");

        prompt.append("=== CITATION RULES (STRICT) ===\n");
        prompt.append("- \"citedRecordTags\" must only contain Record Tags (e.g. \"REC-3\") that appear in the OFFICIAL KPI SUBMISSION RECORD above — never invent a tag.\n");
        prompt.append("- Cite every Record Tag whose submission materially supports that section's claims; omit tags that aren't relevant to that section.\n");
        prompt.append("- A section may have an empty \"citedRecordTags\" array if no submission is directly relevant (e.g. Recommendations may be general).\n\n");

        prompt.append("=== TEXT FORMATTING RULES (apply inside each section's \"text\" field) ===\n");
        prompt.append("- Refer to organizations only by their \"Org-N\" reference — never invent or guess a real organization name.\n");
        prompt.append("- Never mention a Record Tag (e.g. \"REC-3\") anywhere inside \"text\" — Record Tags belong ONLY in the \"citedRecordTags\" array. Write the narrative as if the reader cannot see the tags at all.\n");
        prompt.append("- Do not include markdown headings (#, ##, or numbered headings) inside \"text\" — the heading is already provided as a separate JSON field.\n");
        prompt.append("- Do NOT use horizontal rule lines (e.g. \"---\" or \"***\") anywhere.\n");
        prompt.append("- Do NOT escape asterisks with a backslash (never write \"\\*\"); if a line should start with a literal \"-\" or \"*\" character that is not a bullet or emphasis, rephrase it instead.\n");
        prompt.append("- For any tabular or columnar data (per-KPI breakdowns, comparisons, etc.), you MUST use a proper Markdown pipe table: a header row, then a \"|---|---|\" separator row, then data rows — never align columns with plain spaces.\n");
        prompt.append("- Use **bold** only to emphasize a handful of key terms or figures, and plain text otherwise. Avoid *italics* unless truly necessary. Never nest bold and italics together.\n");
        prompt.append("- Keep every hyphen in compound words exactly as written (e.g. \"period-specific\", \"real-time\") — do not drop them.\n");
        prompt.append("- Write any date you mention in \"Month dd, yyyy\" format (e.g. \"August 1, 2026\"), matching the format already used for Submission Date above — never YYYY-MM-DD.\n");

        // Call Groq LLM API and parse its structured response
        String narrative;
        String sectionsJson = null;
        String status;
        try {
            String rawJson;
            LlmReportPayload payload;
            try {
                rawJson = llmApiClient.generateReport(prompt.toString());
                payload = objectMapper.readValue(rawJson, LlmReportPayload.class);
            } catch (Exception primaryFailure) {
                // Strict json_schema mode was rejected or returned unparsable content — retry once
                // with the looser json_object mode (still guarantees valid JSON, not the exact shape).
                rawJson = llmApiClient.generateReportAsJsonObject(prompt.toString());
                payload = objectMapper.readValue(rawJson, LlmReportPayload.class);
            }

            List<ReportSectionDto> resolvedSections = resolveSections(payload, tagToSubmission, orgIdToName);
            narrative = flattenSections(resolvedSections);
            sectionsJson = objectMapper.writeValueAsString(resolvedSections);
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
        report.setSectionsJson(sectionsJson);
        report.setStatus(status);

        Report saved = reportRepository.save(report);

        return mapToResponse(saved);
    }

    /**
     * Resolves the LLM's raw sections (pseudonymous org codes, ephemeral record tags) into
     * display-ready sections with real organization names and validated source citations.
     * Any cited tag that wasn't actually sent to the LLM is dropped and logged, never persisted.
     */
    private List<ReportSectionDto> resolveSections(
            LlmReportPayload payload, Map<String, KpiSubmission> tagToSubmission, Map<Long, String> orgIdToName) {
        List<ReportSectionDto> result = new ArrayList<>();
        if (payload == null || payload.getSections() == null) {
            return result;
        }

        for (LlmSection section : payload.getSections()) {
            List<String> citedTags = section.getCitedRecordTags() != null
                    ? section.getCitedRecordTags()
                    : List.of();

            List<CitationDto> sources = new ArrayList<>();
            for (String tag : citedTags) {
                KpiSubmission submission = tagToSubmission.get(tag);
                if (submission == null) {
                    log.warn("LLM cited unknown record tag '{}' in section '{}' — dropping", tag, section.getHeading());
                    continue;
                }
                sources.add(new CitationDto(
                        submission.getId(),
                        submission.getKpiDefinition().getName(),
                        submission.getOrganization().getName(),
                        submission.getSubmittedValue(),
                        submission.getKpiDefinition().getTargetValue(),
                        submission.getSubmissionDate()
                ));
            }

            String resolvedText = humanizeDates(stripRecordTagArtifacts(resolvePseudonyms(section.getText(), orgIdToName)));
            result.add(new ReportSectionDto(section.getHeading(), resolvedText, sources));
        }
        return result;
    }

    private static final Pattern RECORD_TAG_PARENTHETICAL =
            Pattern.compile("\\s*\\(\\s*REC-\\d+(?:\\s*,\\s*REC-\\d+)*\\s*\\)");
    private static final Pattern RECORD_TAG_BARE = Pattern.compile("\\bREC-\\d+\\b");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

    /** Replaces every "Org-<id>" pseudonym actually used in this report with the real organization name. */
    private String resolvePseudonyms(String text, Map<Long, String> orgIdToName) {
        if (text == null) {
            return null;
        }
        String resolved = text;
        for (Map.Entry<Long, String> entry : orgIdToName.entrySet()) {
            resolved = resolved.replaceAll(
                    "\\bOrg-" + entry.getKey() + "\\b",
                    Matcher.quoteReplacement(entry.getValue()));
        }
        return resolved;
    }

    /**
     * Removes any literal "REC-N" record tag that leaked into the narrative text, despite the
     * prompt instructing the model to only cite tags via "citedRecordTags", never inline. Record
     * tags are ephemeral, request-scoped identifiers with no meaning to a reader — citation
     * information is already surfaced separately via the resolved "sources" list, so a stray
     * mention here is always noise to strip, never information to preserve.
     */
    private String stripRecordTagArtifacts(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = RECORD_TAG_PARENTHETICAL.matcher(text).replaceAll("");
        cleaned = RECORD_TAG_BARE.matcher(cleaned).replaceAll("");
        // Collapse any doubled-up spacing left behind by a removed mid-sentence tag.
        cleaned = cleaned.replaceAll("[ \\t]{2,}", " ");
        return cleaned;
    }

    /**
     * Rewrites any lingering YYYY-MM-DD date the model wrote into "Month dd, yyyy", matching the
     * style already used everywhere else in the report. The prompt already feeds the model
     * pre-formatted dates and instructs it to write dates this way, but instruction-following isn't
     * guaranteed — this is the same belt-and-suspenders approach as citation-tag validation: the
     * prompt reduces how often this is needed, this guarantees it regardless.
     */
    private String humanizeDates(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = ISO_DATE_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            try {
                replacement = LocalDate.parse(matcher.group()).format(REPORT_DATE_FORMAT);
            } catch (DateTimeParseException e) {
                // Not actually a valid calendar date (e.g. a stray numeric code) — leave it untouched.
                replacement = matcher.group();
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Flattens resolved sections into markdown for backward-compatible rendering (legacy PDF/frontend paths). */
    private String flattenSections(List<ReportSectionDto> sections) {
        StringBuilder sb = new StringBuilder();
        for (ReportSectionDto section : sections) {
            sb.append("## ").append(section.getHeading()).append("\n\n");
            sb.append(section.getText()).append("\n\n");
        }
        return sb.toString();
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
        response.setSections(deserializeSections(report.getSectionsJson()));
        return response;
    }

    /** Raw shape returned by the LLM before tag/pseudonym resolution — never exposed via the API. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LlmReportPayload {
        private List<LlmSection> sections;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LlmSection {
        private String heading;
        private String text;
        private List<String> citedRecordTags;
    }
}