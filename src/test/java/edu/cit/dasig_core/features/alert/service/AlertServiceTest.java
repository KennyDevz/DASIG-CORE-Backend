package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.features.alert.dto.AlertDetailResponse;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(
                alertRepository, kpiSubmissionRepository, kpiDefinitionRepository, userRepository, kpiAlertEvaluatorService);
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

        Alert alert = new Alert();
        alert.setId(1L);
        alert.setKpiDefinitionId(2L);
        alert.setStatus(Alert.STATUS_ACKNOWLEDGED);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(2L);
        kpi.setName("KPI");
        kpi.setDeadline(LocalDate.now().plusDays(10));
        when(kpiDefinitionRepository.findById(2L)).thenReturn(Optional.of(kpi));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Alert is already acknowledged.");
    }

    @Test
    void acknowledgeAlert_marksAcknowledgedAndCleansUpDuplicates() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("DASIG_ADMIN")));

        Alert alert = new Alert();
        alert.setId(1L);
        alert.setKpiDefinitionId(2L);
        alert.setAlertType(Alert.TYPE_OVERDUE);
        alert.setStatus(Alert.STATUS_UNACKNOWLEDGED);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(2L);
        kpi.setName("KPI");
        kpi.setTargetValue(100.0);
        kpi.setDeadline(LocalDate.now().plusDays(10));
        when(kpiDefinitionRepository.findById(2L)).thenReturn(Optional.of(kpi));
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndSubmissionType(any(), any())).thenReturn(List.of());

        Alert duplicate = new Alert();
        duplicate.setId(2L);
        when(alertRepository.findByKpiDefinitionIdAndAlertType(2L, Alert.TYPE_OVERDUE))
                .thenReturn(List.of(alert, duplicate));

        AlertDetailResponse response = alertService.acknowledgeAlert(1L);

        assertThat(alert.getStatus()).isEqualTo(Alert.STATUS_ACKNOWLEDGED);
        assertThat(response.getStatus()).isEqualTo(Alert.STATUS_ACKNOWLEDGED);
        verify(alertRepository).deleteAll(List.of(duplicate));
    }

    @Test
    void existsForSubmission_delegatesToRepository() {
        when(alertRepository.existsBySubmissionId(5L)).thenReturn(true);

        assertThat(alertService.existsForSubmission(5L)).isTrue();
    }
}
