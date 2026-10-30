package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.core.event.KpiEmailEvent;
import edu.cit.dasig_core.core.smtp.EmailService;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.notification.service.NotificationService;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiAlertEvaluatorServiceTest {

    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private KpiSubmissionRepository kpiSubmissionRepository;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private KpiAlertEvaluatorService evaluatorService;

    // 120-day KPI with today in the middle: 50% of time passed, so 50 of 100 is expected by now
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Manila"));
    private static final LocalDate CREATED = TODAY.minusDays(60);
    private static final LocalDate DEADLINE = TODAY.plusDays(60);

    @BeforeEach
    void setUp() {
        evaluatorService = new KpiAlertEvaluatorService(
                kpiDefinitionRepository, kpiSubmissionRepository, alertRepository,
                notificationService, emailService, userRepository, eventPublisher);
        ReflectionTestUtils.setField(evaluatorService, "businessTimezone", "Asia/Manila");
    }

    @Test
    void skipsKpiWithNoDeadline() {
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi(CREATED, null)));

        evaluatorService.evaluateAllKpiAlerts();

        verifyNoInteractions(kpiSubmissionRepository, alertRepository);
    }

    @Test
    void createsAtRiskAlert_whenBehindPace() {
        givenKpi(CREATED, DEADLINE, 20.0); // 20 < half of 50
        givenOpenAlerts();
        when(userRepository.findByRoleAndStatus("DASIG_ADMIN", "Active")).thenReturn(Collections.emptyList());

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository).save(argThat(a ->
                Alert.TYPE_AT_RISK.equals(a.getAlertType()) && Alert.SEVERITY_WARNING.equals(a.getSeverity())));
        verify(notificationService).notifyAlertCreated(any(), argThat(a -> Alert.TYPE_AT_RISK.equals(a.getAlertType())));
        // UC-4.5: the committee's Leads and Members are emailed too
        verify(eventPublisher).publishEvent(KpiEmailEvent.alertCreated(1L, Alert.TYPE_AT_RISK));
    }

    @Test
    void noAlert_whenOnPace() {
        givenKpi(CREATED, DEADLINE, 30.0); // 30 >= half of 50
        givenOpenAlerts();

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void noAtRiskAlert_whenCreationDateMissing() {
        givenKpi(null, DEADLINE, 0.0);
        givenOpenAlerts();

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository, never()).save(any());
    }

    @Test
    void resolvesAtRiskAlert_whenProgressRecovers() {
        givenKpi(CREATED, DEADLINE, 30.0);
        Alert atRisk = openAlert(Alert.TYPE_AT_RISK);
        givenOpenAlerts(atRisk);

        evaluatorService.evaluateAllKpiAlerts();

        assertResolved(atRisk, KpiAlertEvaluatorService.REASON_RECOVERED);
    }

    @Test
    void createsOverdueAlert_whenDeadlinePassedWithoutMeetingTarget() {
        givenKpi(CREATED, TODAY.minusDays(1), 50.0);
        givenOpenAlerts();
        when(userRepository.findByRoleAndStatus("DASIG_ADMIN", "Active")).thenReturn(Collections.emptyList());

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository).save(argThat(a ->
                Alert.TYPE_OVERDUE.equals(a.getAlertType()) && Alert.SEVERITY_CRITICAL.equals(a.getSeverity())));
        verify(notificationService).notifyAlertCreated(any(), argThat(a -> Alert.TYPE_OVERDUE.equals(a.getAlertType())));
        verify(eventPublisher).publishEvent(KpiEmailEvent.alertCreated(1L, Alert.TYPE_OVERDUE));
    }

    @Test
    void escalatesAtRiskToOverdue_whenDeadlinePasses() {
        givenKpi(CREATED, TODAY.minusDays(1), 50.0);
        Alert atRisk = openAlert(Alert.TYPE_AT_RISK);
        givenOpenAlerts(atRisk);
        when(userRepository.findByRoleAndStatus("DASIG_ADMIN", "Active")).thenReturn(Collections.emptyList());

        evaluatorService.evaluateAllKpiAlerts();

        assertResolved(atRisk, KpiAlertEvaluatorService.REASON_ESCALATED);
        verify(alertRepository).save(argThat(a -> Alert.TYPE_OVERDUE.equals(a.getAlertType())));
    }

    @Test
    void doesNotDuplicate_whenOpenAlertAlreadyExists() {
        givenKpi(CREATED, TODAY.minusDays(1), 50.0);
        givenOpenAlerts(openAlert(Alert.TYPE_OVERDUE));

        evaluatorService.evaluateAllKpiAlerts();

        verify(alertRepository, never()).save(any());
        verifyNoInteractions(notificationService, eventPublisher);
    }

    @Test
    void resolvesAllOpenAlerts_whenTargetMet() {
        givenKpi(CREATED, DEADLINE, 100.0);
        Alert atRisk = openAlert(Alert.TYPE_AT_RISK);
        givenOpenAlerts(atRisk);

        evaluatorService.evaluateAllKpiAlerts();

        assertResolved(atRisk, KpiAlertEvaluatorService.REASON_TARGET_MET);
    }

    @Test
    void resolvesOverdueAlert_whenTargetMetAfterDeadline() {
        givenKpi(CREATED, TODAY.minusDays(1), 100.0);
        Alert overdue = openAlert(Alert.TYPE_OVERDUE);
        overdue.setStatus(Alert.STATUS_ACKNOWLEDGED);
        givenOpenAlerts(overdue);

        evaluatorService.evaluateAllKpiAlerts();

        assertResolved(overdue, KpiAlertEvaluatorService.REASON_TARGET_MET_LATE);
    }

    @Test
    void emailsAdminsWithTheCreatedAlertId() {
        givenKpi(CREATED, DEADLINE, 20.0); // behind pace -> AT_RISK alert
        givenOpenAlerts();
        when(alertRepository.save(any())).thenAnswer(invocation -> {
            Alert saved = invocation.getArgument(0);
            saved.setId(77L); // the generated ID is populated on the managed instance
            return saved;
        });

        User admin = new User();
        admin.setName("Admin");
        admin.setEmail("admin@dasig-core.site");
        when(userRepository.findByRoleAndStatus("DASIG_ADMIN", "Active")).thenReturn(List.of(admin));

        evaluatorService.evaluateAllKpiAlerts();

        verify(emailService).sendKpiAlertEmail(
                "admin@dasig-core.site", "Admin",
                Alert.TYPE_AT_RISK, Alert.SEVERITY_WARNING, "KPI 1", 77L);
    }

    // ---- helpers ----

    private KpiDefinition kpi(LocalDate created, LocalDate deadline) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(1L);
        kpi.setName("KPI 1");
        kpi.setTargetValue(100.0);
        kpi.setDeadline(deadline);
        kpi.setDateCreated(created != null ? created.atStartOfDay() : null);
        return kpi;
    }

    private void givenKpi(LocalDate created, LocalDate deadline, double approvedTotal) {
        KpiDefinition kpi = kpi(created, deadline);
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi));

        KpiSubmission submission = new KpiSubmission();
        submission.setKpiDefinition(kpi);
        submission.setSubmittedValue(approvedTotal);
        submission.setSubmissionType(SubmissionType.FINAL);
        submission.setReviewStatus(SubmissionReviewStatus.APPROVED);
        when(kpiSubmissionRepository.findByKpiDefinitionIdInAndSubmissionType(anyCollection(), eq(SubmissionType.FINAL)))
                .thenReturn(List.of(submission));
    }

    private void givenOpenAlerts(Alert... alerts) {
        when(alertRepository.findByStatusIn(anyCollection())).thenReturn(List.of(alerts));
    }

    private Alert openAlert(String type) {
        Alert alert = new Alert();
        alert.setId(10L);
        alert.setKpiDefinitionId(1L);
        alert.setAlertType(type);
        alert.setStatus(Alert.STATUS_UNACKNOWLEDGED);
        return alert;
    }

    private void assertResolved(Alert alert, String reason) {
        assertThat(alert.getStatus()).isEqualTo(Alert.STATUS_RESOLVED);
        assertThat(alert.getResolutionReason()).isEqualTo(reason);
        assertThat(alert.getResolvedAt()).isNotNull();
    }
}
