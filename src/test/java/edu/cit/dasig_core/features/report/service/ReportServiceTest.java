package edu.cit.dasig_core.features.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.report.client.LLMApiClient;
import edu.cit.dasig_core.features.report.dto.ReportResponse;
import edu.cit.dasig_core.features.report.dto.ReportSectionDto;
import edu.cit.dasig_core.features.report.model.Report;
import edu.cit.dasig_core.features.report.model.ReportType;
import edu.cit.dasig_core.features.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private KpiSubmissionRepository submissionRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private LLMApiClient llmApiClient;
    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private CommitteeRepository committeeRepository;

    // Real (not mocked) ObjectMapper — these tests exercise actual JSON parsing/serialization
    // of the LLM's structured response, not a stubbed pass-through. JavaTimeModule is registered
    // explicitly to mirror Spring Boot's auto-configured ObjectMapper bean (which registers it
    // automatically via jackson-datatype-jsr310, already transitively on the classpath) — a plain
    // `new ObjectMapper()` here would fail on CitationDto.submissionDate (a LocalDate) in a way
    // production code never would.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(
                submissionRepository, reportRepository, llmApiClient, kpiDefinitionRepository, committeeRepository,
                objectMapper);
    }

    /** A minimal, schema-valid structured response with no citations — used where citation content doesn't matter. */
    private static final String MINIMAL_STRUCTURED_RESPONSE = """
            {
              "sections": [
                {"heading": "Overall Performance Summary", "text": "All good.", "citedRecordTags": []},
                {"heading": "Underperforming KPIs", "text": "None.", "citedRecordTags": []},
                {"heading": "Major Progress Points", "text": "None.", "citedRecordTags": []},
                {"heading": "Recommendations", "text": "Keep going.", "citedRecordTags": []}
              ]
            }
            """;

    @Test
    void generateCommitteeReport_throwsWhenCommitteeNotFound() {
        when(committeeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Committee not found with ID: 1");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void generateCommitteeReport_savesGeneratedStatusOnLlmSuccess() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");
        committee.setOrganizations(List.of());
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(llmApiClient.generateReport(anyString())).thenReturn(MINIMAL_STRUCTURED_RESPONSE);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0001");
            return report;
        });

        ReportResponse response = reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(response.getStatus()).isEqualTo("GENERATED");
        assertThat(response.getCommitteeId()).isEqualTo(1L);
        assertThat(response.getReportType()).isEqualTo(ReportType.COMMITTEE);
        assertThat(response.getSections()).hasSize(4);
        assertThat(response.getSections().get(0).getHeading()).isEqualTo("Overall Performance Summary");
        assertThat(response.getNarrativeText()).contains("Overall Performance Summary");
    }

    @Test
    void generateCommitteeReport_savesFailedStatusWhenLlmThrows() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");
        committee.setOrganizations(List.of());
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        // Both the strict-schema attempt and the json_object fallback fail — the report must
        // still save as FAILED rather than throwing out of buildAndSaveReport entirely.
        when(llmApiClient.generateReport(anyString())).thenThrow(new RuntimeException("Groq is down"));
        when(llmApiClient.generateReportAsJsonObject(anyString())).thenThrow(new RuntimeException("Groq is down"));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0002");
            return report;
        });

        ReportResponse response = reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getNarrativeText()).contains("Groq is down");
        assertThat(response.getSections()).isNullOrEmpty();
    }

    @Test
    void generateCommitteeReport_fallsBackToJsonObjectModeWhenStrictSchemaRejected() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");
        committee.setOrganizations(List.of());
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        // Strict json_schema mode fails (e.g. unsupported by the configured model);
        // the looser json_object fallback succeeds and the report should still generate.
        when(llmApiClient.generateReport(anyString())).thenThrow(new RuntimeException("response_format not supported"));
        when(llmApiClient.generateReportAsJsonObject(anyString())).thenReturn(MINIMAL_STRUCTURED_RESPONSE);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0006");
            return report;
        });

        ReportResponse response = reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(response.getStatus()).isEqualTo("GENERATED");
        assertThat(response.getSections()).hasSize(4);
        verify(llmApiClient).generateReportAsJsonObject(anyString());
    }

    @Test
    void generateCommitteeReport_withNoOrganizations_skipsSubmissionLookup() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Empty Committee");
        committee.setOrganizations(List.of());
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(llmApiClient.generateReport(anyString())).thenReturn(MINIMAL_STRUCTURED_RESPONSE);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0003");
            return report;
        });

        reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now());

        verify(submissionRepository, never()).findByOrganizationIdIn(any());
    }

    @Test
    void generateKpiReport_throwsWhenKpiNotFound() {
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.generateKpiReport(1L, LocalDate.now().minusMonths(1), LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI not found with ID: 1");
    }

    @Test
    void generateKpiReport_savesReportScopedToKpisCommittee() {
        Committee committee = new Committee();
        committee.setId(3L);
        committee.setName("Committee X");
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(5L);
        kpi.setName("KPI Five");
        kpi.setCommittee(committee);
        when(kpiDefinitionRepository.findById(5L)).thenReturn(Optional.of(kpi));
        when(submissionRepository.findByKpiDefinitionId(5L)).thenReturn(List.of());
        when(llmApiClient.generateReport(anyString())).thenReturn(MINIMAL_STRUCTURED_RESPONSE);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0004");
            return report;
        });

        ReportResponse response = reportService.generateKpiReport(5L, LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(response.getKpiDefinitionId()).isEqualTo(5L);
        assertThat(response.getCommitteeId()).isEqualTo(3L);
        assertThat(response.getReportType()).isEqualTo(ReportType.KPI);
    }

    @Test
    void getReport_throwsWhenNotFound() {
        when(reportRepository.findById("PR-2026-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReport("PR-2026-9999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Report not found with ID: PR-2026-9999");
    }

    @Test
    void getAllReports_mapsEveryReport() {
        Report a = new Report();
        a.setId("PR-2026-0001");
        a.setCommitteeId(1L);
        a.setReportType(ReportType.COMMITTEE);
        a.setPeriodFrom(LocalDate.now().minusMonths(1));
        a.setPeriodTo(LocalDate.now());
        a.setNarrativeText("text");
        a.setStatus("GENERATED");
        // sectionsJson intentionally left null — an older, pre-citations report
        when(reportRepository.findAllByOrderByGeneratedAtDesc()).thenReturn(List.of(a));
        when(committeeRepository.findById(1L)).thenReturn(Optional.empty());

        List<ReportResponse> responses = reportService.getAllReports();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo("PR-2026-0001");
        assertThat(responses.get(0).getSections()).isNull();
    }

    @Test
    void exportAsPdf_throwsWhenReportNotFound() {
        when(reportRepository.findById("PR-2026-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.exportAsPdf("PR-2026-9999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Report not found with ID: PR-2026-9999");
    }

    @Test
    void exportAsPdf_producesNonEmptyPdfBytesForSimpleNarrative() {
        Report report = new Report();
        report.setId("PR-2026-0005");
        report.setPeriodFrom(LocalDate.now().minusMonths(1));
        report.setPeriodTo(LocalDate.now());
        report.setGeneratedAt(java.time.LocalDateTime.now());
        report.setNarrativeText("1. Overall Performance Summary\nEverything is on track.");
        when(reportRepository.findById("PR-2026-0005")).thenReturn(Optional.of(report));

        byte[] pdfBytes = reportService.exportAsPdf("PR-2026-0005");

        assertThat(pdfBytes).isNotEmpty();
        // PDF files start with the "%PDF" magic header
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportAsPdf_rendersStructuredSectionsAndSourcesForCitationBackedReport() {
        Report report = new Report();
        report.setId("PR-2026-0008");
        report.setPeriodFrom(LocalDate.now().minusMonths(1));
        report.setPeriodTo(LocalDate.now());
        report.setGeneratedAt(java.time.LocalDateTime.now());
        report.setNarrativeText("## Overall Performance Summary\n\nAll good.\n\n");
        report.setSectionsJson("""
                [{"heading":"Overall Performance Summary","text":"All good.",
                  "sources":[{"submissionId":142,"kpiName":"Revenue Growth","organizationName":"Cebu TBI Hub",
                              "submittedValue":8.0,"targetValue":25.0,"submissionDate":"2026-06-28"}]}]
                """);
        when(reportRepository.findById("PR-2026-0008")).thenReturn(Optional.of(report));

        byte[] pdfBytes = reportService.exportAsPdf("PR-2026-0008");

        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportAsPdf_deduplicatesASubmissionCitedByMultipleSectionsIntoOneNumberedEntry() throws Exception {
        Report report = new Report();
        report.setId("PR-2026-0011");
        report.setPeriodFrom(LocalDate.now().minusMonths(1));
        report.setPeriodTo(LocalDate.now());
        report.setGeneratedAt(java.time.LocalDateTime.now());
        report.setNarrativeText("placeholder — sectionsJson takes precedence when present");
        // Submission 142 backs both sections; 200 backs only the second. The full citation detail
        // for 142 should appear exactly once in the final list, not once per section that cites it.
        report.setSectionsJson("""
                [
                  {"heading":"Overall Performance Summary","text":"All good.",
                    "sources":[{"submissionId":142,"kpiName":"Revenue Growth","organizationName":"Cebu TBI Hub",
                                "submittedValue":8.0,"targetValue":25.0,"submissionDate":"2026-06-28"}]},
                  {"heading":"Underperforming KPIs","text":"See above.",
                    "sources":[{"submissionId":142,"kpiName":"Revenue Growth","organizationName":"Cebu TBI Hub",
                                "submittedValue":8.0,"targetValue":25.0,"submissionDate":"2026-06-28"},
                               {"submissionId":200,"kpiName":"Mentee Placement","organizationName":"Startup Incubator PH",
                                "submittedValue":42.0,"targetValue":70.0,"submissionDate":"2026-06-30"}]}
                ]
                """);
        when(reportRepository.findById("PR-2026-0011")).thenReturn(Optional.of(report));

        byte[] pdfBytes = reportService.exportAsPdf("PR-2026-0011");

        com.itextpdf.text.pdf.PdfReader reader = new com.itextpdf.text.pdf.PdfReader(pdfBytes);
        String text = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, 1);
        reader.close();

        assertThat(countOccurrences(text, "Submission #142")).isEqualTo(1);
        assertThat(countOccurrences(text, "Submission #200")).isEqualTo(1);
        // Reference numbers are appended inline at the end of each section's text now, not on a
        // separate "Sources: ..." line — section 1 cites only #1, section 2 cites #1 and #2.
        assertThat(text).contains("[1]");
        assertThat(text).contains("[1,2]");
        // The word "Sources" itself should appear exactly once — as the final list's heading —
        // since the per-section references no longer carry that label.
        assertThat(countOccurrences(text, "Sources")).isEqualTo(1);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    @Test
    void generateCommitteeReport_pseudonymizesOrgAndExcludesNotesFromPrompt_thenResolvesRealDataInResponse() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");

        Organization org = new Organization();
        org.setId(42L);
        org.setName("Cebu TBI Hub");
        committee.setOrganizations(List.of(org));

        LocalDate deadline = LocalDate.now().plusMonths(2);
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(7L);
        kpi.setName("Incubatee Revenue Growth");
        kpi.setUnit("%");
        kpi.setTargetValue(25.0);
        kpi.setThreshold(10.0);
        kpi.setReportingFrequency(ReportingFrequency.ONE_TIME);
        kpi.setDeadline(deadline);
        kpi.setCommittee(committee);

        String period = "Due by " + deadline.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH));

        KpiSubmission submission = new KpiSubmission();
        submission.setId(142L);
        submission.setKpiDefinition(kpi);
        submission.setOrganization(org);
        submission.setSubmittedValue(8.0);
        submission.setReportingPeriod(period);
        submission.setSubmissionDate(LocalDate.now());
        submission.setSubmissionType(SubmissionType.FINAL);
        // Must never reach the third-party LLM per the Data Minimization Policy
        submission.setNotes("Confidential board discussion about layoffs");

        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(submissionRepository.findByOrganizationIdIn(List.of(42L))).thenReturn(List.of(submission));

        // The LLM cites one real tag (REC-1) and one tag it was never given (REC-99, hallucinated)
        String llmResponse = """
                {
                  "sections": [
                    {"heading": "Overall Performance Summary", "text": "Org-42 is below target.", "citedRecordTags": ["REC-1", "REC-99"]},
                    {"heading": "Underperforming KPIs", "text": "Org-42 needs support.", "citedRecordTags": ["REC-1"]},
                    {"heading": "Major Progress Points", "text": "None this period.", "citedRecordTags": []},
                    {"heading": "Recommendations", "text": "Follow up with Org-42.", "citedRecordTags": []}
                  ]
                }
                """;
        when(llmApiClient.generateReport(anyString())).thenReturn(llmResponse);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0007");
            return report;
        });

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        ReportResponse response = reportService.generateCommitteeReport(
                1L, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(3));

        verify(llmApiClient).generateReport(promptCaptor.capture());
        String sentPrompt = promptCaptor.getValue();

        // Data minimization: the real org name and Notes must never reach the third-party LLM
        assertThat(sentPrompt).doesNotContain("Cebu TBI Hub");
        assertThat(sentPrompt).doesNotContain("Confidential board discussion");
        assertThat(sentPrompt).contains("Org-42");
        assertThat(sentPrompt).contains("REC-1");

        assertThat(response.getStatus()).isEqualTo("GENERATED");
        assertThat(response.getSections()).hasSize(4);

        ReportSectionDto overallSummary = response.getSections().get(0);
        // Resolution: real org name substituted back into the narrative before storage/display
        assertThat(overallSummary.getText()).contains("Cebu TBI Hub");
        assertThat(overallSummary.getText()).doesNotContain("Org-42");

        // The hallucinated "REC-99" tag was dropped rather than failing the whole report
        assertThat(overallSummary.getSources()).hasSize(1);
        assertThat(overallSummary.getSources().get(0).getSubmissionId()).isEqualTo(142L);
        assertThat(overallSummary.getSources().get(0).getOrganizationName()).isEqualTo("Cebu TBI Hub");
        assertThat(overallSummary.getSources().get(0).getKpiName()).isEqualTo("Incubatee Revenue Growth");
        assertThat(overallSummary.getSources().get(0).getSubmittedValue()).isEqualTo(8.0);
        assertThat(overallSummary.getSources().get(0).getTargetValue()).isEqualTo(25.0);

        assertThat(response.getNarrativeText()).contains("Cebu TBI Hub");
        assertThat(response.getNarrativeText()).doesNotContain("Org-42");
    }

    @Test
    void generateCommitteeReport_stripsRecordTagsThatLeakIntoNarrativeText() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");
        committee.setOrganizations(List.of());
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));

        // The model was told to cite tags only via "citedRecordTags", never inline — but it
        // (bare-mentioned and parenthetically) ignored that instruction anyway. Neither form of
        // "REC-1" should survive into the resolved narrative shown to a user.
        String llmResponse = """
                {
                  "sections": [
                    {"heading": "Overall Performance Summary", "text": "REC-1 shows steady growth (REC-1) this period.", "citedRecordTags": []},
                    {"heading": "Underperforming KPIs", "text": "None.", "citedRecordTags": []},
                    {"heading": "Major Progress Points", "text": "None.", "citedRecordTags": []},
                    {"heading": "Recommendations", "text": "Keep going.", "citedRecordTags": []}
                  ]
                }
                """;
        when(llmApiClient.generateReport(anyString())).thenReturn(llmResponse);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0009");
            return report;
        });

        ReportResponse response = reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(response.getStatus()).isEqualTo("GENERATED");
        String resolvedText = response.getSections().get(0).getText();
        assertThat(resolvedText).doesNotContain("REC-1");
        assertThat(resolvedText).contains("shows steady growth");
        assertThat(resolvedText).contains("this period");
    }

    @Test
    void generateCommitteeReport_rewritesIsoDatesInNarrativeTextToReadableFormat() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");
        committee.setOrganizations(List.of());
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));

        // Despite the prompt instructing "Month dd, yyyy" and feeding pre-formatted dates, the
        // model wrote an ISO-style date anyway — this must still come out readable, not verbatim.
        String llmResponse = """
                {
                  "sections": [
                    {"heading": "Overall Performance Summary", "text": "As of 2026-08-01, progress remains steady.", "citedRecordTags": []},
                    {"heading": "Underperforming KPIs", "text": "None.", "citedRecordTags": []},
                    {"heading": "Major Progress Points", "text": "None.", "citedRecordTags": []},
                    {"heading": "Recommendations", "text": "Follow up before 2026-09-15.", "citedRecordTags": []}
                  ]
                }
                """;
        when(llmApiClient.generateReport(anyString())).thenReturn(llmResponse);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0010");
            return report;
        });

        ReportResponse response = reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(response.getStatus()).isEqualTo("GENERATED");
        assertThat(response.getSections().get(0).getText())
                .isEqualTo("As of August 01, 2026, progress remains steady.");
        assertThat(response.getSections().get(3).getText())
                .isEqualTo("Follow up before September 15, 2026.");
    }
}
