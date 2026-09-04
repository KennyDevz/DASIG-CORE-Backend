package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KpiAlertEvaluatorServiceTest {

    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private KpiSubmissionRepository kpiSubmissionRepository;
    @Mock
    private AlertRepository alertRepository;

    private KpiAlertEvaluatorService evaluatorService;

    @BeforeEach
    void setUp() {
        evaluatorService = new KpiAlertEvaluatorService(kpiDefinitionRepository, kpiSubmissionRepository, alertRepository);
        ReflectionTestUtils.setField(evaluatorService, "businessTimezone", "Asia/Manila");
    }

    private KpiDefinition kpi(Long id, LocalDate deadline, double target) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(id);
        kpi.setName("KPI " + id);
        kpi.setDeadline(deadline);
        kpi.setTargetValue(target);
        return kpi;
    }

    private KpiSubmission approvedFinal(double value) {
        KpiSubmission submission = new KpiSubmission();
        submission.setSubmittedValue(value);
        submission.setSubmissionType(SubmissionType.FINAL);
        submission.setReviewStatus(SubmissionReviewStatus.APPROVED);
        return submission;
    }

    @Test
    void evaluateAllKpiAlerts_skipsKpiWithNullDeadline() {
        KpiDefinition kpi = kpi(1L, null, 100.0);
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi));

        evaluatorService.evaluateAllKpiAlerts();

        verifyNoInteractions(kpiSubmissionRepository, alertRepository);
    }

    @Test
    void evaluateAllKpiAlerts_skipsWhenTargetAlreadyAchieved() {
        KpiDefinition kpi = kpi(1L, LocalDate.now().minusDays(1), 100.0);
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi));
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndSubmissionType(1L, SubmissionType.FINAL))
                .thenReturn(List.of(approvedFinal(150.0)));

        evaluatorService.evaluateAllKpiAlerts();

        verifyNoInteractions(alertRepository);
    }

    @Test
    void evaluateAllKpiAlerts_createsOverdueAlertWhenDeadlinePassedWithoutMeetingTarget() {
        KpiDefinition kpi = kpi(1L, LocalDate.now().minusDays(1), 100.0);
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi));
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndSubmissionType(1L, SubmissionType.FINAL))
                .thenReturn(List.of(approvedFinal(50.0)));
        when(alertRepository.findByKpiDefinitionIdAndAlertType(1L, Alert.TYPE_OVERDUE)).thenReturn(List.of());

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository).save(argThat(a ->
                a.getAlertType().equals(Alert.TYPE_OVERDUE) && a.getSeverity().equals(Alert.SEVERITY_CRITICAL)));
    }

    @Test
    void evaluateAllKpiAlerts_createsAtRiskAlertWhenDeadlineSoonAndProgressBelow50Percent() {
        KpiDefinition kpi = kpi(1L, LocalDate.now().plusDays(30), 100.0);
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi));
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndSubmissionType(1L, SubmissionType.FINAL))
                .thenReturn(List.of(approvedFinal(20.0)));
        when(alertRepository.findByKpiDefinitionIdAndAlertType(1L, Alert.TYPE_AT_RISK)).thenReturn(List.of());

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository).save(argThat(a ->
                a.getAlertType().equals(Alert.TYPE_AT_RISK) && a.getSeverity().equals(Alert.SEVERITY_WARNING)));
    }

    @Test
    void evaluateAllKpiAlerts_doesNotAlertWhenDeadlineSoonButProgressAbove50Percent() {
        KpiDefinition kpi = kpi(1L, LocalDate.now().plusDays(30), 100.0);
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi));
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndSubmissionType(1L, SubmissionType.FINAL))
                .thenReturn(List.of(approvedFinal(60.0)));

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository, never()).save(any());
    }

    @Test
    void evaluateAllKpiAlerts_doesNotDuplicateWhenAlertAlreadyExists() {
        KpiDefinition kpi = kpi(1L, LocalDate.now().minusDays(1), 100.0);
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi));
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndSubmissionType(1L, SubmissionType.FINAL))
                .thenReturn(List.of(approvedFinal(50.0)));

        Alert existing = new Alert();
        existing.setId(1L);
        existing.setStatus(Alert.STATUS_UNACKNOWLEDGED);
        when(alertRepository.findByKpiDefinitionIdAndAlertType(1L, Alert.TYPE_OVERDUE)).thenReturn(List.of(existing));

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository, never()).save(any());
    }

    @Test
    void evaluateAllKpiAlerts_cleansUpDuplicateUnacknowledgedAlertsWhenAnAcknowledgedOneExists() {
        KpiDefinition kpi = kpi(1L, LocalDate.now().minusDays(1), 100.0);
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi));
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndSubmissionType(1L, SubmissionType.FINAL))
                .thenReturn(List.of(approvedFinal(50.0)));

        Alert acknowledged = new Alert();
        acknowledged.setId(1L);
        acknowledged.setStatus(Alert.STATUS_ACKNOWLEDGED);
        Alert unacknowledgedDuplicate = new Alert();
        unacknowledgedDuplicate.setId(2L);
        unacknowledgedDuplicate.setStatus(Alert.STATUS_UNACKNOWLEDGED);
        when(alertRepository.findByKpiDefinitionIdAndAlertType(1L, Alert.TYPE_OVERDUE))
                .thenReturn(List.of(acknowledged, unacknowledgedDuplicate));

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository).deleteAll(List.of(unacknowledgedDuplicate));
        verify(alertRepository, never()).save(any());
    }
}
