package edu.cit.dasig_core.features.dashboard.service;

import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.dashboard.dto.DashboardResponse;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private KpiSubmissionRepository kpiSubmissionRepository;
    @Mock
    private OrganizationRepository organizationRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                userRepository, kpiDefinitionRepository, kpiSubmissionRepository, organizationRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private User user(String role, Long orgId) {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setRole(role);
        user.setOrganizationId(orgId);
        user.setStatus("Active");
        return user;
    }

    private KpiDefinition kpi(Long id, Long committeeId) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(id);
        kpi.setName("KPI " + id);
        kpi.setDescription("desc");
        kpi.setTargetValue(100.0);
        kpi.setUnit("count");
        kpi.setThreshold(50.0);
        kpi.setDeadline(LocalDate.now().plusMonths(6));
        // Hibernate's @CreationTimestamp always populates dateCreated once persisted; set it
        // explicitly here since this KpiDefinition is never actually saved through JPA.
        kpi.setDateCreated(java.time.LocalDateTime.now().minusMonths(1));
        kpi.setReportingFrequency(ReportingFrequency.QUARTERLY);
        Committee committee = new Committee();
        committee.setId(committeeId);
        kpi.setCommittee(committee);
        return kpi;
    }

    @Test
    void getDashboardForCurrentUser_throwsWhenNotAuthenticated() {
        assertThatThrownBy(() -> dashboardService.getDashboardForCurrentUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authentication is required.");
    }

    @Test
    void getDashboardForCurrentUser_throwsWhenAccountNotActive() {
        User inactive = user("STAFF", 1L);
        inactive.setStatus("Inactive");
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> dashboardService.getDashboardForCurrentUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account is not active.");
    }

    @Test
    void getDashboardForCurrentUser_throwsWhenNonAdminHasNoOrganization() {
        User staff = user("STAFF", null);
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> dashboardService.getDashboardForCurrentUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization is required for this role.");
    }

    @Test
    void getDashboardForCurrentUser_adminSeesAllKpiDefinitions() {
        User admin = user("DASIG_ADMIN", null);
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(admin));
        when(kpiDefinitionRepository.findAll()).thenReturn(List.of(kpi(1L, 5L), kpi(2L, 6L)));
        when(kpiSubmissionRepository.findByKpiDefinitionId(any())).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboardForCurrentUser(null);

        assertThat(response.getKpis()).hasSize(2);
        assertThat(response.getRole()).isEqualTo("DASIG_ADMIN");
    }

    @Test
    void getDashboardForCurrentUser_staffSeesOnlyCommitteeScopedKpis() {
        User staff = user("STAFF", 9L);
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(staff));
        when(kpiDefinitionRepository.findByCommittee_Organizations_Id(9L)).thenReturn(List.of(kpi(1L, 5L)));
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(any(), any(), any()))
                .thenReturn(List.of());
        when(organizationRepository.findById(9L)).thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboardForCurrentUser(null);

        assertThat(response.getKpis()).hasSize(1);
        assertThat(response.getOrganizationId()).isEqualTo(9L);
    }

    @Test
    void getKpiPeriodHistory_throwsWhenKpiNotFound() {
        User admin = user("DASIG_ADMIN", null);
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(admin));
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getKpiPeriodHistory(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KPI Definition not found with ID: 1");
    }

    @Test
    void getKpiPeriodHistory_throwsWhenStaffOrganizationNotUnderKpiCommittee() {
        User staff = user("STAFF", 9L);
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(staff));
        when(kpiDefinitionRepository.findById(1L)).thenReturn(Optional.of(kpi(1L, 5L)));
        when(organizationRepository.findByCommitteeId(5L)).thenReturn(List.of());

        assertThatThrownBy(() -> dashboardService.getKpiPeriodHistory(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You do not have access to this KPI.");
    }
}
