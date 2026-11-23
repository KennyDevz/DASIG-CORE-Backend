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

import edu.cit.dasig_core.features.kpisubmission.util.PerformanceStatusClassifier;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpi.util.ReportingPeriodResolver;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.cit.dasig_core.features.report.client.LLMException;
import java.util.function.Supplier;

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

    @Value("${app.business-timezone:Asia/Manila}")
    private String businessTimezone;

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessTimezone));
    }

    public ReportResponse generateCommitteeReport(Long committeeId, LocalDate periodFrom, LocalDate periodTo) {
        validatePeriod(periodFrom, periodTo);

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

        // Every active KPI of the committee goes into the KPI summary, even without submissions in the window
        List<KpiDefinition> committeeKpis = kpiDefinitionRepository.findByCommitteeId(committeeId).stream()
                .filter(kpi -> !kpi.isArchived())
                .toList();

        return buildAndSaveReport(submissions, committeeKpis, committeeId, ReportType.COMMITTEE, null,
                periodFrom, periodTo, contextHeader);
    }

    public ReportResponse generateKpiReport(Long kpiDefinitionId, LocalDate periodFrom, LocalDate periodTo) {
        validatePeriod(periodFrom, periodTo);

        // 1. Fetch the KPI to find out which committee owns it
        KpiDefinition kpi = kpiDefinitionRepository.findById(kpiDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("KPI not found with ID: " + kpiDefinitionId));

        // 2. Fetch submissions for this specific KPI (across every organization under the committee that reports on it)
        List<KpiSubmission> submissions = submissionRepository.findByKpiDefinitionId(kpiDefinitionId);

        String contextHeader = "Generate a specific performance report focused strictly on the following single KPI, "
                + "covering all incubator organizations under the \"" + kpi.getCommittee().getName() + "\" committee that report on it.\n\n";

        return buildAndSaveReport(submissions, List.of(kpi), kpi.getCommittee().getId(), ReportType.KPI, kpiDefinitionId,
                periodFrom, periodTo, contextHeader);
    }

    // Guards against an inverted range (periodFrom after periodTo), which would otherwise silently
    // match zero submissions in buildAndSaveReport's date filter and produce an empty report
    // instead of a clear error telling the caller to fix the dates.
    private void validatePeriod(LocalDate periodFrom, LocalDate periodTo) {
        if (periodFrom.isAfter(periodTo)) {
            throw new IllegalArgumentException("Period from date must not be after period to date.");
        }
        if (periodTo.isAfter(today())) {
            throw new IllegalArgumentException("Period to date must not be later than today.");
        }
    }

    public ReportResponse getReport(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));
        return mapToResponse(report);
    }

    public List<ReportResponse> getAllReports() {
        List<Report> reports = reportRepository.findAllByOrderByGeneratedAtDesc();

        // Committee and KPI names in two queries instead of two per report
        Map<Long, String> committeeNames = committeeRepository
                .findAllById(reports.stream().map(Report::getCommitteeId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Committee::getId, Committee::getName));
        Map<Long, String> kpiNames = kpiDefinitionRepository
                .findAllById(reports.stream().map(Report::getKpiDefinitionId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(KpiDefinition::getId, KpiDefinition::getName));

        return reports.stream()
                .map(report -> mapToResponse(report,
                        committeeNames.get(report.getCommitteeId()),
                        kpiNames.get(report.getKpiDefinitionId())))
                .toList();
    }

    public byte[] exportAsPdf(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found with ID: " + reportId));

        if ("FAILED".equals(report.getStatus())) {
            throw new IllegalArgumentException("A failed report cannot be exported.");
        }

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
                        String line = (idx + 1) + ". " + c.getKpiName() + " (" + c.getOrganizationName() + ") — "
                                + c.getSubmissionReference() + ", " + c.getSubmissionDate().format(formatter);
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
            throw new RuntimeException("Failed to generate PDF", e);
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
            if (text == null) {
                return;
            }
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
            List<KpiDefinition> summaryKpis,
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

        // KPI figures are calculated here, exactly like the dashboard, so the AI never adds up numbers itself
        LocalDate today = today();
        List<KpiSummaryRow> kpiSummary = buildKpiSummary(summaryKpis, allSubmissions, filteredWindowSubmissions, today);

        // The user message carries only this report's data; the fixed rules are in REPORT_RULES (system message)
        StringBuilder prompt = new StringBuilder();
        prompt.append(contextHeader);

        prompt.append("=== REPORT PARAMETERS ===\n");
        prompt.append("Reporting Window: ").append(periodFrom.format(REPORT_DATE_FORMAT))
                .append(" to ").append(periodTo.format(REPORT_DATE_FORMAT)).append("\n");
        prompt.append("Report Date (today): ").append(today.format(REPORT_DATE_FORMAT)).append("\n\n");

        prompt.append("=== KPI SUMMARY (as of today, same as the dashboard - use these exact numbers) ===\n");
        if (kpiSummary.isEmpty()) {
            prompt.append("No KPIs.\n\n");
        }
        for (KpiSummaryRow row : kpiSummary) {
            prompt.append(String.format(
                    "- KPI: %s\n" +
                            "  * Target: %s\n" +
                            "  * Approved Total to Date: %s\n" +
                            "  * Achievement Rate: %.1f%%\n" +
                            "  * Current Status: %s\n" +
                            "  * Remaining to Target: %s\n" +
                            "  * Deadline: %s (%s)\n" +
                            "  * Approved in Reporting Window: %s\n\n",
                    row.name(),
                    amount(row.target(), row.unit()),
                    amount(row.approvedTotal(), row.unit()),
                    row.achievementRate(),
                    row.status(),
                    amount(row.remaining(), row.unit()),
                    row.deadline().format(REPORT_DATE_FORMAT), daysLabel(row.daysLeft()),
                    amount(row.approvedInWindow(), row.unit())
            ));
        }

        // Every organization/submission identity sent to the third-party LLM is pseudonymized per the
        // Data Minimization Policy: organizations become "Org-<id>" (the id is already a non-identifying
        // opaque value, so no separate persisted mapping is needed) and submissions get an ephemeral
        // "REC-N" tag scoped only to this single report generation. Both are resolved back to real
        // values below, after the LLM responds - the LLM never sees a real org name, and no field
        // outside the permitted list (e.g. Notes) is ever included.
        Map<String, KpiSubmission> tagToSubmission = new LinkedHashMap<>();
        Map<Long, String> orgIdToName = new LinkedHashMap<>();

        prompt.append("=== OFFICIAL KPI SUBMISSION RECORD (approved submissions in the reporting window) ===\n");
        if (filteredWindowSubmissions.isEmpty()) {
            prompt.append("No official submissions found within this window.\n\n");
        } else {
            int tagCounter = 1;
            for (KpiSubmission s : filteredWindowSubmissions) {
                String recordTag = "REC-" + (tagCounter++);
                tagToSubmission.put(recordTag, s);
                orgIdToName.putIfAbsent(s.getOrganization().getId(), s.getOrganization().getName());
                prompt.append(String.format(
                        "- Record Tag: %s\n" +
                                "  * KPI Name: %s\n" +
                                "  * Organization Reference: Org-%d\n" +
                                "  * Value Submitted: %s\n" +
                                "  * Submission Date: %s\n\n",
                        recordTag,
                        s.getKpiDefinition().getName(),
                        s.getOrganization().getId(),
                        amount(s.getSubmittedValue(), s.getKpiDefinition().getUnit()),
                        s.getSubmissionDate().format(REPORT_DATE_FORMAT)
                ));
            }
        }

        // Each organization's total per KPI for the window, so the AI never has to add records together
        prompt.append("=== ORGANIZATION CONTRIBUTIONS IN THE REPORTING WINDOW (use these totals) ===\n");
        Map<Long, Map<Long, Double>> contributions = new LinkedHashMap<>();
        Map<Long, KpiDefinition> kpisById = new LinkedHashMap<>();
        for (KpiSubmission s : filteredWindowSubmissions) {
            kpisById.putIfAbsent(s.getKpiDefinition().getId(), s.getKpiDefinition());
            contributions.computeIfAbsent(s.getKpiDefinition().getId(), id -> new LinkedHashMap<>())
                    .merge(s.getOrganization().getId(), s.getSubmittedValue(), Double::sum);
        }
        if (contributions.isEmpty()) {
            prompt.append("None.\n");
        }
        contributions.forEach((kpiId, byOrg) -> {
            KpiDefinition kpi = kpisById.get(kpiId);
            String orgTotals = byOrg.entrySet().stream()
                    .map(e -> "Org-" + e.getKey() + ": " + amount(e.getValue(), kpi.getUnit()))
                    .collect(Collectors.joining("; "));
            prompt.append("- ").append(kpi.getName()).append(": ").append(orgTotals).append("\n");
        });

        // Call Groq LLM API and parse its structured response
        String narrative = null;
        String sectionsJson = null;
        String status;
        LLMException.Reason failureReason = null;

        try {
            List<ReportSectionDto> resolvedSections =
                    generateSections(REPORT_RULES, prompt.toString(), tagToSubmission, orgIdToName);
            // The KPI summary table is built in code and placed at the top of the first section,
            // so its figures always match the dashboard
            String summaryTable = buildSummaryTable(kpiSummary, today);
            if (!summaryTable.isEmpty()) {
                ReportSectionDto first = resolvedSections.get(0);
                first.setText(summaryTable + "\n" + first.getText());
            }
            narrative = flattenSections(resolvedSections);
            sectionsJson = objectMapper.writeValueAsString(resolvedSections);
            status = "GENERATED";
        } catch (LLMException e) {
            log.error("Report generation failed [{}]", e.getReason(), e);
            failureReason = e.getReason();
            status = "FAILED";
        } catch (Exception e) {
            log.error("Report generation failed unexpectedly", e);
            failureReason = LLMException.Reason.INVALID_RESPONSE;
            status = "FAILED";
        }

        Report report = new Report();
        report.setCommitteeId(committeeId);
        report.setReportType(reportType);
        report.setKpiDefinitionId(kpiDefinitionId);
        report.setPeriodFrom(periodFrom);
        report.setPeriodTo(periodTo);
        report.setNarrativeText(narrative);       // null when FAILED
        report.setSectionsJson(sectionsJson);     // null when FAILED
        report.setStatus(status);
        report.setFailureReason(failureReason);   // null when GENERATED

        Report saved = reportRepository.save(report);

        return mapToResponse(saved);
    }

    /** Fixed report rules, sent as the system message. The report's data goes in the user message. */
    private static final String REPORT_RULES = """
            You are a performance analyst writing reports for the DASIG Consortium, a group of technology \
            business incubators. Your readers are consortium staff, not data analysts.

            === HOW PERFORMANCE IS MEASURED ===
            - Each KPI has a target and a deadline. Its progress is the total of approved values from all \
            organizations under the committee.
            - The KPI SUMMARY gives each KPI's approved total, achievement rate, current status, remaining amount \
            and days left, calculated by the system exactly as shown on the dashboard. Use these numbers exactly \
            as given. Never recalculate, re-round or estimate them.
            - The ORGANIZATION CONTRIBUTIONS block gives each organization's total for the reporting window. Use \
            those totals; never add record values together yourself.
            - Status is pre-computed as "Current Status". Never recompute or second-guess it. Meanings:
              - Completed: the target has been reached.
              - Overdue: the deadline has passed and the target was not reached.
              - At Risk: at least 25% of the KPI's timeline (from its creation date to its deadline) has passed, \
            and its approved progress is below half of the progress expected by now.
              - On Track: every other case.

            === REQUIRED OUTPUT FORMAT (STRICT JSON) ===
            Respond with a JSON object containing a "sections" array of exactly 4 objects, in this exact order, \
            each with a "heading", a "text" (the narrative body), and a "citedRecordTags" array:
            1. heading: "Overall Performance Summary" - the overall picture: how many KPIs are Completed, On Track, \
            At Risk and Overdue, and what changed during the reporting window.
            2. heading: "Underperforming KPIs" - every KPI whose status is At Risk or Overdue, with how much is \
            still needed to reach its target and how many days are left (or how many days overdue). If there are \
            none, say so in one sentence.
            3. heading: "Major Progress Points" - KPIs that are Completed or On Track, and the organizations whose \
            contributions in the reporting window stood out.
            4. heading: "Recommendations" - 2 to 4 specific actions. Each must name the KPI and its gap, for \
            example: "Intellectual Property Filings needs 8.00 more filings in the remaining 30 days." Start with \
            At Risk and Overdue KPIs.

            === WRITING STYLE ===
            - Plain, professional English for non-technical readers. Short sentences.
            - Start each section with one sentence that states its key point.
            - Keep each section to about 120 words. Use "- " bullet points for lists of KPIs or actions, at most \
            5 bullets per section.
            - Never use technical terms such as "scaled target", "cumulative progression", "pace ratio" or \
            "expected threshold".
            - Do not write tables. A KPI summary table is added to the report automatically.

            === CITATION RULES (STRICT) ===
            - "citedRecordTags" must only contain Record Tags (e.g. "REC-3") that appear in the OFFICIAL KPI \
            SUBMISSION RECORD - never invent a tag.
            - Cite every Record Tag whose submission supports that section's statements; omit tags that are not \
            relevant to that section.
            - A section may have an empty "citedRecordTags" array if no submission is directly relevant.

            === TEXT FORMATTING RULES (apply inside each section's "text" field) ===
            - Write status names exactly as: Completed, On Track, At Risk, Overdue. Never in all caps or with \
            underscores. Never use color names or "delayed" as a status - a missed deadline is always Overdue.
            - Refer to organizations only by their "Org-N" reference, written exactly like that with the hyphen \
            (e.g. "Org-3"), and never invent or guess a real organization name.
            - Only write an "Org-N" reference that appears in the OFFICIAL KPI SUBMISSION RECORD or the \
            ORGANIZATION CONTRIBUTIONS. Never mention an organization that has no record there.
            - Never mention a Record Tag (e.g. "REC-3") inside "text" - Record Tags belong only in \
            "citedRecordTags".
            - Never show arithmetic (e.g. "5 + 10 = 15"); state only the numbers given.
            - Do not include markdown headings (#, ##, or numbered headings) inside "text".
            - Do not use horizontal rule lines (e.g. "---" or "***").
            - Do not escape asterisks with a backslash (never write "\\*").
            - Use **bold** only for a few key figures or KPI names. Avoid *italics*.
            - Write any date as "Month dd, yyyy" (e.g. "August 01, 2026"), never YYYY-MM-DD.
            """;

    /** One KPI's figures, calculated the same way as the dashboard. */
    private record KpiSummaryRow(
            String name, String unit, double target, double approvedTotal, double achievementRate,
            String status, double remaining, LocalDate deadline, long daysLeft, double approvedInWindow) {
    }

    /**
     * Same calculation as the Admin dashboard: every FINAL submission of the KPI (all organizations),
     * the KPI's current reporting period, then KpiPeriodProgressCalculator for total, rate and status.
     */
    private List<KpiSummaryRow> buildKpiSummary(
            List<KpiDefinition> kpis, List<KpiSubmission> allSubmissions, List<KpiSubmission> windowSubmissions,
            LocalDate today) {
        List<KpiSummaryRow> rows = new ArrayList<>();
        for (KpiDefinition kpi : kpis) {
            if (kpi.getDeadline() == null) {
                continue;
            }
            List<KpiSubmission> finals = allSubmissions.stream()
                    .filter(s -> s.getKpiDefinition().getId().equals(kpi.getId())
                            && s.getSubmissionType() == SubmissionType.FINAL)
                    .toList();
            LocalDate start = kpi.getDateCreated() != null ? kpi.getDateCreated().toLocalDate() : today;
            double target = kpi.getTargetValue() != null ? kpi.getTargetValue() : 0.0;
            String period = ReportingPeriodResolver.resolveCurrentPeriod(
                    kpi.getReportingFrequency(), kpi.getDeadline(), start, today);

            double total = 0.0;
            double rate = 0.0;
            String status;
            if (period != null) {
                KpiPeriodProgressCalculator.KpiPeriodProgress progress =
                        KpiPeriodProgressCalculator.calculateExisting(kpi, period, finals);
                total = progress.cumulativeSubmittedValue();
                rate = progress.achievementRate();
                status = progress.performanceStatus();
            } else {
                status = PerformanceStatusClassifier.classify(0.0, target, start, kpi.getDeadline());
            }

            double inWindow = windowSubmissions.stream()
                    .filter(s -> s.getKpiDefinition().getId().equals(kpi.getId()))
                    .mapToDouble(KpiSubmission::getSubmittedValue)
                    .sum();
            rows.add(new KpiSummaryRow(
                    kpi.getName(), kpi.getUnit(), target, total, rate, toClassifyPerformanceStatus(status),
                    Math.max(target - total, 0.0), kpi.getDeadline(),
                    ChronoUnit.DAYS.between(today, kpi.getDeadline()), inWindow));
        }
        return rows;
    }

    /** Markdown table of the KPI summary; renders on screen and in the PDF like any other table. */
    private String buildSummaryTable(List<KpiSummaryRow> rows, LocalDate today) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**KPI status as of ").append(today.format(REPORT_DATE_FORMAT)).append("**\n\n");
        sb.append("| KPI | Target | Approved to Date | Achievement | Status | Approved in Period |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (KpiSummaryRow row : rows) {
            sb.append("| ").append(row.name().replace("|", "/"))
                    .append(" | ").append(amount(row.target(), row.unit()))
                    .append(" | ").append(amount(row.approvedTotal(), row.unit()))
                    .append(" | ").append(String.format("%.1f%%", row.achievementRate()))
                    .append(" | ").append(row.status())
                    .append(" | ").append(amount(row.approvedInWindow(), row.unit()))
                    .append(" |\n");
        }
        return sb.toString();
    }

    private static String amount(Double value, String unit) {
        double number = value != null ? value : 0.0;
        return (String.format("%.2f", number) + " " + (unit != null ? unit : "")).trim();
    }

    private static String daysLabel(long daysLeft) {
        if (daysLeft == 0) {
            return "due today";
        }
        long days = Math.abs(daysLeft);
        String unit = days == 1 ? " day" : " days";
        return daysLeft > 0 ? days + unit + " left" : days + unit + " overdue";
    }

    private static final int EXPECTED_SECTION_COUNT = 4;
    private static final long RETRY_BACKOFF_MS = 2000;

    /**
     * Calls the LLM and returns validated, resolved sections. Throws LLMException on any failure.
     * - Falls back to json_object mode only when strict json_schema was rejected or its output was unparsable.
     * - Retries once (inside callWithRetry) on rate limit / service unavailable.
     */
    private List<ReportSectionDto> generateSections(
            String systemPrompt, String prompt, Map<String, KpiSubmission> tagToSubmission, Map<Long, String> orgIdToName) {
        LlmReportPayload payload;
        try {
            payload = parsePayload(callWithRetry(() -> llmApiClient.generateReport(systemPrompt, prompt)));
        } catch (LLMException e) {
            if (e.getReason() != LLMException.Reason.SCHEMA_REJECTED
                    && e.getReason() != LLMException.Reason.INVALID_RESPONSE) {
                throw e;
            }
            log.warn("Strict json_schema mode failed [{}], retrying in json_object mode", e.getReason());
            payload = parsePayload(callWithRetry(() -> llmApiClient.generateReportAsJsonObject(systemPrompt, prompt)));
        }

        validatePayload(payload);
        return resolveSections(payload, tagToSubmission, orgIdToName);
    }

    private String callWithRetry(Supplier<String> call) {
        try {
            return call.get();
        } catch (LLMException e) {
            if (e.getReason() != LLMException.Reason.RATE_LIMITED
                    && e.getReason() != LLMException.Reason.UNAVAILABLE) {
                throw e;
            }
            log.warn("Transient LLM failure [{}], retrying once in {} ms", e.getReason(), RETRY_BACKOFF_MS);
            try {
                Thread.sleep(RETRY_BACKOFF_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return call.get();
        }
    }

    private LlmReportPayload parsePayload(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, LlmReportPayload.class);
        } catch (Exception e) {
            throw new LLMException(LLMException.Reason.INVALID_RESPONSE, e);
        }
    }

    private void validatePayload(LlmReportPayload payload) {
        List<LlmSection> sections = payload == null ? null : payload.getSections();
        if (sections == null || sections.size() != EXPECTED_SECTION_COUNT) {
            throw new LLMException(LLMException.Reason.INVALID_RESPONSE);
        }
        for (LlmSection s : sections) {
            if (s.getHeading() == null || s.getHeading().isBlank()
                    || s.getText() == null || s.getText().isBlank()) {
                throw new LLMException(LLMException.Reason.INVALID_RESPONSE);
            }
        }
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
                        submission.getReferenceCode(),
                        submission.getKpiDefinition().getName(),
                        submission.getOrganization().getName(),
                        submission.getSubmittedValue(),
                        submission.getKpiDefinition().getTargetValue(),
                        submission.getSubmissionDate()
                ));
            }

            String resolvedText = humanizeDates(stripRecordTagArtifacts(stripUnresolvedOrgPseudonyms(
                    resolvePseudonyms(section.getText(), orgIdToName), section.getHeading())));
            result.add(new ReportSectionDto(section.getHeading(), resolvedText, sources));
        }
        return result;
    }

    private static final Pattern RECORD_TAG_PARENTHETICAL =
            Pattern.compile("\\s*\\(\\s*REC-\\d+(?:\\s*,\\s*REC-\\d+)*\\s*\\)");
    private static final Pattern RECORD_TAG_BARE = Pattern.compile("\\bREC-\\d+\\b");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

    // Matches "Org-3", "org 3", and "Org3" alike — the model doesn't reliably keep the exact
    // "Org-N" separator/casing it was given (observed writing "Org1"/"Org2" with no separator at all).
    private static final Pattern ORG_PSEUDONYM = Pattern.compile("\\bOrg[-\\s]?\\d+\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Replaces every "Org-<id>" pseudonym actually used in this report with the real organization
     * name, regardless of the exact separator/casing the model used between "Org" and the id.
     */
    private String resolvePseudonyms(String text, Map<Long, String> orgIdToName) {
        if (text == null) {
            return null;
        }
        String resolved = text;
        for (Map.Entry<Long, String> entry : orgIdToName.entrySet()) {
            resolved = Pattern.compile("\\bOrg[-\\s]?" + entry.getKey() + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(resolved)
                    .replaceAll(Matcher.quoteReplacement(entry.getValue()));
        }
        return resolved;
    }

    /**
     * Safety net for any "Org-N" pseudonym that {@link #resolvePseudonyms} couldn't map to a real
     * name — e.g. the model referenced an organization it was never given a record for (common in
     * KPI-scoped reports, whose prompt invites discussion of "all organizations under the
     * committee" even though only organizations with a submission in the window get a pseudonym
     * mapping). The org identity behind an unmapped pseudonym is unrecoverable, so fall back to a
     * generic phrase rather than ever leak the internal "Org-N" placeholder to a reader.
     */
    private String stripUnresolvedOrgPseudonyms(String text, String sectionHeading) {
        if (text == null) {
            return null;
        }
        String cleaned = ORG_PSEUDONYM.matcher(text).replaceAll("the organization");
        if (!cleaned.equals(text)) {
            log.warn("LLM referenced an unresolved organization pseudonym in section '{}' — replaced with a generic phrase", sectionHeading);
        }
        return cleaned;
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
        String committeeName = committeeRepository.findById(report.getCommitteeId())
                .map(Committee::getName)
                .orElse(null);
        String kpiName = report.getKpiDefinitionId() != null
                ? kpiDefinitionRepository.findById(report.getKpiDefinitionId()).map(KpiDefinition::getName).orElse(null)
                : null;
        return mapToResponse(report, committeeName, kpiName);
    }

    private ReportResponse mapToResponse(Report report, String committeeName, String kpiName) {
        ReportResponse response = new ReportResponse();
        response.setId(report.getId());
        response.setCommitteeId(report.getCommitteeId());
        response.setCommitteeName(committeeName);
        response.setReportType(report.getReportType());
        response.setKpiDefinitionId(report.getKpiDefinitionId());
        response.setKpiName(kpiName);
        response.setPeriodFrom(report.getPeriodFrom());
        response.setPeriodTo(report.getPeriodTo());
        response.setNarrativeText(report.getNarrativeText());
        response.setStatus(report.getStatus());
        if (report.getFailureReason() != null) {
            response.setFailureMessage(report.getFailureReason().getUserMessage());
        }
        response.setGeneratedAt(report.getGeneratedAt());
        response.setSections(deserializeSections(report.getSectionsJson()));
        return response;
    }

    private String toClassifyPerformanceStatus(String status) {
        if (status == null) {
            return "On Track";
        }
        switch (status) {
            case PerformanceStatusClassifier.ON_TRACK:  return "On Track";
            case PerformanceStatusClassifier.COMPLETED: return "Completed";
            case PerformanceStatusClassifier.OVERDUE:   return "Overdue";
            case PerformanceStatusClassifier.AT_RISK:   return "At Risk";
            default:                                    return "On Track";//To Clarify
        }
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