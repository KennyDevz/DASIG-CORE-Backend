package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.features.alert.dto.AlertDetailResponse;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.model.OverdueExplanation;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.alert.repository.OverdueExplanationRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.notification.service.NotificationService;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private KpiSubmissionRepository kpiSubmissionRepository;
    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KpiAlertEvaluatorService kpiAlertEvaluatorService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private OverdueExplanationRepository overdueExplanationRepository;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(
                alertRepository, kpiSubmissionRepository, kpiDefinitionRepository, userRepository,
                kpiAlertEvaluatorService, notificationService, overdueExplanationRepository);
        ReflectionTestUtils.setField(alertService, "businessTimezone", "Asia/Manila");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private User user(String role) {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setRole(role);
        user.setStatus("Active");
        return user;
    }

    @Test
    void getAllAlerts_throwsWhenNotDasigAdmin() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF")));

        assertThatThrownBy(() -> alertService.getAllAlerts())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only DASIG Admins can view alerts.");

        verify(kpiAlertEvaluatorService, never()).evaluateAllKpiAlerts();
    }

    @Test
    void getAllAlerts_evaluatesThenReturnsAlertsForAdmin() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));
        when(alertRepository.findAllByOrderByDetectedAtDesc()).thenReturn(List.of(new Alert()));

        assertThat(alertService.getAllAlerts()).hasSize(1);
        verify(kpiAlertEvaluatorService).evaluateAllKpiAlerts();
    }

    @Test
    void getAllAlertDetails_evaluatesThenReturnsDetailsWithApprovedTotal() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));
        when(alertRepository.findAllByOrderByDetectedAtDesc()).thenReturn(List.of(alert(Alert.STATUS_UNACKNOWLEDGED)));
        when(kpiDefinitionRepository.findAllById(any())).thenReturn(List.of(kpi()));
        when(kpiAlertEvaluatorService.approvedTotals(any())).thenReturn(Map.of(2L, 40.0));

        List<AlertDetailResponse> details = alertService.getAllAlertDetails();

        verify(kpiAlertEvaluatorService).evaluateAllKpiAlerts();
        assertThat(details).hasSize(1);
        assertThat(details.get(0).getKpiName()).isEqualTo("KPI");
        assertThat(details.get(0).getCumulativeValue()).isEqualTo(40.0);
        assertThat(details.get(0).getExplanationStatus()).isEqualTo(OverdueExplanationService.STATUS_AWAITING);
    }

    @Test
    void getAllAlertDetails_skipsAlertsWhoseKpiWasDeleted() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));
        when(alertRepository.findAllByOrderByDetectedAtDesc()).thenReturn(List.of(alert(Alert.STATUS_UNACKNOWLEDGED)));

        assertThat(alertService.getAllAlertDetails()).isEmpty();
    }

    @Test
    void countUnacknowledgedAlerts_countsWithoutEvaluating() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));
        when(alertRepository.countByStatus(Alert.STATUS_UNACKNOWLEDGED)).thenReturn(3L);

        assertThat(alertService.countUnacknowledgedAlerts()).isEqualTo(3L);
        verify(kpiAlertEvaluatorService, never()).evaluateAllKpiAlerts();
    }

    @Test
    void getAlertById_throwsWhenAlertNotFound() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));
        when(alertRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.getAlertById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Alert not found with ID: 1");
    }

    @Test
    void acknowledgeAlert_throwsWhenAlreadyAcknowledged() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert(Alert.STATUS_ACKNOWLEDGED)));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(1L, "Noted"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Alert is already acknowledged.");
    }

    @Test
    void acknowledgeAlert_throwsWhenResolved() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert(Alert.STATUS_RESOLVED)));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(1L, "Noted"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resolved alerts cannot be acknowledged.");
        verify(alertRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void acknowledgeAlert_savesRemarkAndAdmin_withoutDeletingOtherAlerts() {
        authenticateAs("user@example.com");
        User admin = user("DASIG_ADMIN");
        admin.setName("Admin User");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.findAllById(any())).thenReturn(List.of(admin));

        Alert alert = alert(Alert.STATUS_UNACKNOWLEDGED);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kpiDefinitionRepository.findAllById(any())).thenReturn(List.of(kpi()));
        when(kpiDefinitionRepository.findById(2L)).thenReturn(Optional.of(kpi()));

        AlertDetailResponse response = alertService.acknowledgeAlert(1L, "  Will follow up with the committee.  ");

        assertThat(alert.getStatus()).isEqualTo(Alert.STATUS_ACKNOWLEDGED);
        assertThat(alert.getAdminRemark()).isEqualTo("Will follow up with the committee.");
        assertThat(alert.getRemarkedById()).isEqualTo(1L);
        assertThat(alert.getRemarkedAt()).isNotNull();
        assertThat(response.getAdminRemark()).isEqualTo("Will follow up with the committee.");
        assertThat(response.getRemarkedByName()).isEqualTo("Admin User");
        verify(alertRepository, never()).deleteAll(any());
        verify(notificationService).notifyAlertAcknowledged(any(KpiDefinition.class), eq(alert));
    }

    @Test
    void getAlertById_includesResolutionAndExpectedByNow() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));

        Alert alert = alert(Alert.STATUS_RESOLVED);
        alert.setResolutionReason("Target met");
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(kpiDefinitionRepository.findAllById(any())).thenReturn(List.of(kpi()));

        AlertDetailResponse response = alertService.getAlertById(1L);

        assertThat(response.getResolutionReason()).isEqualTo("Target met");
        assertThat(response.getExpectedByNow()).isEqualTo(50.0); // half of the timeline has passed
    }

    @Test
    void getAlertById_showsCommitteeLeadExplanation_forOverdueAlert() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));

        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert(Alert.STATUS_UNACKNOWLEDGED)));
        when(kpiDefinitionRepository.findAllById(any())).thenReturn(List.of(kpi()));

        OverdueExplanation explanation = new OverdueExplanation();
        explanation.setAlertId(1L);
        explanation.setExplanation("Two member organizations joined late.");
        explanation.setAuthorId(5L);
        User lead = user("TBI_MANAGER");
        lead.setId(5L);
        lead.setName("Committee Lead");
        when(overdueExplanationRepository.findByAlertIdIn(any())).thenReturn(List.of(explanation));
        when(userRepository.findAllById(any())).thenReturn(List.of(lead));

        AlertDetailResponse response = alertService.getAlertById(1L);

        assertThat(response.getExplanationStatus()).isEqualTo(OverdueExplanationService.STATUS_EXPLAINED);
        assertThat(response.getExplanation()).isEqualTo("Two member organizations joined late.");
        assertThat(response.getExplainedByName()).isEqualTo("Committee Lead");
    }

    private Alert alert(String status) {
        Alert alert = new Alert();
        alert.setId(1L);
        alert.setKpiDefinitionId(2L);
        alert.setAlertType(Alert.TYPE_OVERDUE);
        alert.setStatus(status);
        return alert;
    }

    // 20-day KPI with today in the middle, target 100
    private KpiDefinition kpi() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(2L);
        kpi.setName("KPI");
        kpi.setTargetValue(100.0);
        kpi.setDateCreated(today.minusDays(10).atStartOfDay());
        kpi.setDeadline(today.plusDays(10));
        return kpi;
    }

    @Test
    void existsForSubmission_delegatesToRepository() {
        when(alertRepository.existsBySubmissionId(5L)).thenReturn(true);

        assertThat(alertService.existsForSubmission(5L)).isTrue();
    }
}
