package edu.cit.dasig_core.features.report.service;

import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.report.client.LLMApiClient;
import edu.cit.dasig_core.features.report.dto.ReportResponse;
import edu.cit.dasig_core.features.report.model.Report;
import edu.cit.dasig_core.features.report.model.ReportType;
import edu.cit.dasig_core.features.report.repository.ReportRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
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

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(
                submissionRepository, reportRepository, llmApiClient, kpiDefinitionRepository, committeeRepository);
    }

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
        when(llmApiClient.generateReport(anyString())).thenReturn("1. Overall Performance Summary\nAll good.");
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0001");
            return report;
        });

        ReportResponse response = reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(response.getStatus()).isEqualTo("GENERATED");
        assertThat(response.getCommitteeId()).isEqualTo(1L);
        assertThat(response.getReportType()).isEqualTo(ReportType.COMMITTEE);
    }

    @Test
    void generateCommitteeReport_savesFailedStatusWhenLlmThrows() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");
        committee.setOrganizations(List.of());
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(llmApiClient.generateReport(anyString())).thenThrow(new RuntimeException("Groq is down"));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId("PR-2026-0002");
            return report;
        });

        ReportResponse response = reportService.generateCommitteeReport(1L, LocalDate.now().minusMonths(1), LocalDate.now());

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getNarrativeText()).contains("Groq is down");
    }

    @Test
    void generateCommitteeReport_withNoOrganizations_skipsSubmissionLookup() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Empty Committee");
        committee.setOrganizations(List.of());
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(llmApiClient.generateReport(anyString())).thenReturn("narrative");
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
        when(llmApiClient.generateReport(anyString())).thenReturn("narrative");
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
        when(reportRepository.findAllByOrderByGeneratedAtDesc()).thenReturn(List.of(a));
        when(committeeRepository.findById(1L)).thenReturn(Optional.empty());

        List<ReportResponse> responses = reportService.getAllReports();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo("PR-2026-0001");
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
}
