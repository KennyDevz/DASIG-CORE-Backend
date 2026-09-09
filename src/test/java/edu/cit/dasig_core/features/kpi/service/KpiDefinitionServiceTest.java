package edu.cit.dasig_core.features.kpi.service;

import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.kpi.dto.CreateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.dto.KpiDefinitionResponse;
import edu.cit.dasig_core.features.kpi.dto.UpdateKpiDefinitionRequest;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.SubmissionDocumentRepository;
import edu.cit.dasig_core.features.notification.repository.NotificationRepository;
import edu.cit.dasig_core.features.notification.service.NotificationService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KpiDefinitionServiceTest {

    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private CommitteeRepository committeeRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private KpiSubmissionRepository kpiSubmissionRepository;
    @Mock
    private SubmissionDocumentRepository submissionDocumentRepository;

    private KpiDefinitionService kpiDefinitionService;

    @BeforeEach
    void setUp() {
        kpiDefinitionService = new KpiDefinitionService(
                kpiDefinitionRepository,
                committeeRepository,
                notificationService,
                notificationRepository,
                alertRepository,
                kpiSubmissionRepository,
                submissionDocumentRepository
        );
    }

    private CreateKpiDefinitionRequest createRequest() {
        CreateKpiDefinitionRequest request = new CreateKpiDefinitionRequest();
        request.setName("Revenue");
        request.setDescription("desc");
        request.setTargetValue(1000.0);
        request.setUnit("PHP");
        request.setDeadline(LocalDate.now().plusMonths(6));
        request.setThreshold(80.0);
        request.setCommitteeId(1L);
        request.setReportingFrequency(ReportingFrequency.QUARTERLY);
        return request;
    }

    @Test
    void createKpiDefinition_throwsWhenCommitteeNotFound() {
        when(committeeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiDefinitionService.createKpiDefinition(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Committee not found with ID: 1");

        verify(kpiDefinitionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createKpiDefinition_savesAndTriggersDeadlineNotificationsOnSuccess() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");
        when(committeeRepository.findById(1L)).thenReturn(Optional.of(committee));
        when(kpiDefinitionRepository.saveAndFlush(any(KpiDefinition.class))).thenAnswer(invocation -> {
            KpiDefinition kpi = invocation.getArgument(0);
            kpi.setId(10L);
            return kpi;
        });

        KpiDefinitionResponse response = kpiDefinitionService.createKpiDefinition(createRequest());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCommitteeId()).isEqualTo(1L);
        verify(notificationService).createDeadlineNotificationsForKpi(any(KpiDefinition.class));
    }

    @Test
    void updateKpiDefinition_throwsWhenNotFound() {
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.empty());

        UpdateKpiDefinitionRequest request = new UpdateKpiDefinitionRequest();
        request.setName("X");
        request.setDescription("desc");
        request.setTargetValue(1.0);
        request.setUnit("unit");
        request.setDeadline(LocalDate.now());

        assertThatThrownBy(() -> kpiDefinitionService.updateKpiDefinition(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI Definition not found with ID: 1");
    }

    @Test
    void updateKpiDefinition_updatesAndTriggersDeadlineNotificationsOnSuccess() {
        Committee committee = new Committee();
        committee.setId(1L);
        committee.setName("Tech Committee");

        KpiDefinition existingKpi = new KpiDefinition();
        existingKpi.setId(1L);
        existingKpi.setName("Old Name");
        existingKpi.setDescription("Old Desc");
        existingKpi.setTargetValue(500.0);
        existingKpi.setUnit("USD");
        existingKpi.setDeadline(LocalDate.now().plusMonths(3));
        existingKpi.setThreshold(50.0);
        existingKpi.setReportingFrequency(ReportingFrequency.ONE_TIME);
        existingKpi.setCommittee(committee);

        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.of(existingKpi));
        when(kpiDefinitionRepository.saveAndFlush(any(KpiDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateKpiDefinitionRequest request = new UpdateKpiDefinitionRequest();
        request.setName("Updated Revenue");
        request.setDescription("Updated description");
        request.setTargetValue(2000.0);
        request.setUnit("PHP");
        LocalDate newDeadline = LocalDate.now().plusMonths(9);
        request.setDeadline(newDeadline);
        request.setThreshold(90.0);
        request.setReportingFrequency(ReportingFrequency.QUARTERLY);

        KpiDefinitionResponse response = kpiDefinitionService.updateKpiDefinition(1L, request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Updated Revenue");
        assertThat(response.getDescription()).isEqualTo("Updated description");
        assertThat(response.getTargetValue()).isEqualTo(2000.0);
        assertThat(response.getUnit()).isEqualTo("PHP");
        assertThat(response.getDeadline()).isEqualTo(newDeadline);
        assertThat(response.getThreshold()).isEqualTo(90.0);
        assertThat(response.getReportingFrequency()).isEqualTo(ReportingFrequency.QUARTERLY);
        assertThat(response.getCommitteeId()).isEqualTo(1L);
        assertThat(response.getCommitteeName()).isEqualTo("Tech Committee");

        verify(kpiDefinitionRepository).saveAndFlush(existingKpi);
        verify(notificationService).createDeadlineNotificationsForKpi(existingKpi);
    }


    @Test
    void deleteKpiDefinition_throwsWhenNotFound() {
        when(kpiDefinitionRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> kpiDefinitionService.deleteKpiDefinition(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI Definition not found with ID: 1");

        verify(kpiDefinitionRepository, never()).deleteById(any());
    }

    @Test
    void deleteKpiDefinition_deletesWhenExists() {
        when(kpiDefinitionRepository.existsById(1L)).thenReturn(true);
        KpiSubmission submission = new KpiSubmission();
        submission.setId(10L);
        when(kpiSubmissionRepository.findByKpiDefinitionId(1L)).thenReturn(List.of(submission));

        kpiDefinitionService.deleteKpiDefinition(1L);

        verify(notificationRepository).deleteByKpiDefinitionId(1L);
        verify(alertRepository).deleteByKpiDefinitionId(1L);
        verify(alertRepository).deleteBySubmissionId(10L);
        verify(submissionDocumentRepository).deleteBySubmissionId(10L);
        verify(kpiSubmissionRepository).deleteAll(List.of(submission));
        verify(kpiDefinitionRepository).deleteById(1L);
    }

    @Test
    void archiveKpiDefinition_setsArchivedAndCleansAlertsAndNotifications() {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(1L);
        kpi.setStatus(KpiDefinition.STATUS_ACTIVE);
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.of(kpi));
        when(kpiDefinitionRepository.saveAndFlush(any(KpiDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KpiDefinitionResponse response = kpiDefinitionService.archiveKpiDefinition(1L);

        assertThat(response.getStatus()).isEqualTo(KpiDefinition.STATUS_ARCHIVED);
        assertThat(response.isArchived()).isTrue();
        verify(alertRepository).deleteByKpiDefinitionId(1L);
        verify(notificationRepository).deleteByKpiDefinitionId(1L);
    }

    @Test
    void unarchiveKpiDefinition_restoresActiveAndReEvaluatesNotifications() {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(1L);
        kpi.setStatus(KpiDefinition.STATUS_ARCHIVED);
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.of(kpi));
        when(kpiDefinitionRepository.saveAndFlush(any(KpiDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KpiDefinitionResponse response = kpiDefinitionService.unarchiveKpiDefinition(1L);

        assertThat(response.getStatus()).isEqualTo(KpiDefinition.STATUS_ACTIVE);
        assertThat(response.isArchived()).isFalse();
        verify(notificationService).createDeadlineNotificationsForKpi(any(KpiDefinition.class));
    }

    @Test
    void getKpiDefinitionById_throwsWhenNotFound() {
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiDefinitionService.getKpiDefinitionById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI Definition not found with ID: 1");
    }

    @Test
    void getAllKpiDefinitions_mapsEveryDefinition() {
        KpiDefinition a = new KpiDefinition();
        a.setId(1L);
        a.setName("A");
        KpiDefinition b = new KpiDefinition();
        b.setId(2L);
        b.setName("B");
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(a, b));

        List<KpiDefinitionResponse> responses = kpiDefinitionService.getAllKpiDefinitions();

        assertThat(responses).extracting(KpiDefinitionResponse::getName).containsExactlyInAnyOrder("A", "B");
    }
    @Test
    void updateKpiDefinition_oneTimeKpiUpdatesReportingPeriodOnExistingSubmissionsWhenDeadlineExtended() {
        KpiDefinition existingKpi = new KpiDefinition();
        existingKpi.setId(10L);
        existingKpi.setName("Old KPI");
        existingKpi.setDescription("Old Desc");
        existingKpi.setTargetValue(50.0);
        existingKpi.setUnit("units");
        existingKpi.setDeadline(LocalDate.of(2026, 9, 15));
        existingKpi.setReportingFrequency(ReportingFrequency.ONE_TIME);

        UpdateKpiDefinitionRequest request = new UpdateKpiDefinitionRequest();
        request.setName("Updated KPI");
        request.setDescription("Updated Desc");
        request.setTargetValue(50.0);
        request.setUnit("units");
        request.setDeadline(LocalDate.of(2026, 10, 31));
        request.setThreshold(100.0);
        request.setReportingFrequency(ReportingFrequency.ONE_TIME);

        KpiSubmission submission = new KpiSubmission();
        submission.setId(1L);
        submission.setReportingPeriod("Due by Sep 15, 2026");
        submission.setSubmittedValue(25.0);

        when(kpiDefinitionRepository.findById(10L)).thenReturn(Optional.of(existingKpi));
        when(kpiDefinitionRepository.saveAndFlush(any(KpiDefinition.class))).thenAnswer(i -> i.getArgument(0));
        when(kpiSubmissionRepository.findByKpiDefinitionId(10L)).thenReturn(List.of(submission));

        KpiDefinitionResponse response = kpiDefinitionService.updateKpiDefinition(10L, request);

        assertThat(response.getDeadline()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(submission.getReportingPeriod()).isEqualTo("Due by Oct 31, 2026");
        verify(kpiSubmissionRepository).save(submission);
    }
}
