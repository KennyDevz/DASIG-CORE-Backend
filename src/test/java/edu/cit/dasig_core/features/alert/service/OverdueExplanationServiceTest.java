package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.features.alert.dto.CommitteeOverdueAlertResponse;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.model.OverdueExplanation;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.alert.repository.OverdueExplanationRepository;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverdueExplanationServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private OverdueExplanationRepository overdueExplanationRepository;
    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private UserRepository userRepository;

    private OverdueExplanationService service;

    @BeforeEach
    void setUp() {
        service = new OverdueExplanationService(
                alertRepository, overdueExplanationRepository, kpiDefinitionRepository, userRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("lead@example.com", null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOverdueAlerts_listsAlertsOfLeadsCommittees_withExplanationStatus() {
        givenUser("TBI_MANAGER", committee(3L));
        when(kpiDefinitionRepository.findByCommitteeId(3L)).thenReturn(List.of(kpi(3L)));
        Alert explained = overdueAlert(1L);
        Alert awaiting = overdueAlert(2L);
        when(alertRepository.findByKpiDefinitionIdAndAlertType(20L, Alert.TYPE_OVERDUE))
                .thenReturn(List.of(explained, awaiting));
        OverdueExplanation explanation = new OverdueExplanation();
        explanation.setExplanation("Late start.");
        when(overdueExplanationRepository.findByAlertId(1L)).thenReturn(Optional.of(explanation));
        when(overdueExplanationRepository.findByAlertId(2L)).thenReturn(Optional.empty());

        List<CommitteeOverdueAlertResponse> result = service.getOverdueAlertsForCommitteeLead();

        assertThat(result).extracting(CommitteeOverdueAlertResponse::getExplanationStatus)
                .containsExactlyInAnyOrder(OverdueExplanationService.STATUS_EXPLAINED, OverdueExplanationService.STATUS_AWAITING);
    }

    @Test
    void submitExplanation_savesOneExplanationForOwnCommittee() {
        User lead = givenUser("TBI_MANAGER", committee(3L));
        when(alertRepository.findById(1L)).thenReturn(Optional.of(overdueAlert(1L)));
        when(kpiDefinitionRepository.findById(20L)).thenReturn(Optional.of(kpi(3L)));
        when(overdueExplanationRepository.existsByAlertId(1L)).thenReturn(false);

        service.submitExplanation(1L, "  Two member organizations joined late.  ");

        verify(overdueExplanationRepository).save(argThat(e ->
                e.getAlertId().equals(1L)
                        && e.getKpiDefinitionId().equals(20L)
                        && e.getExplanation().equals("Two member organizations joined late.")
                        && e.getAuthorId().equals(lead.getId())
                        && e.getAuthorRole().equals("TBI_MANAGER")));
    }

    @Test
    void submitExplanation_rejectsSecondExplanation() {
        givenUser("TBI_MANAGER", committee(3L));
        when(alertRepository.findById(1L)).thenReturn(Optional.of(overdueAlert(1L)));
        when(kpiDefinitionRepository.findById(20L)).thenReturn(Optional.of(kpi(3L)));
        when(overdueExplanationRepository.existsByAlertId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.submitExplanation(1L, "Again"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An explanation has already been submitted for this alert.");
        verify(overdueExplanationRepository, never()).save(any());
    }

    @Test
    void submitExplanation_rejectsOtherCommitteesKpi() {
        givenUser("TBI_MANAGER", committee(99L));
        when(alertRepository.findById(1L)).thenReturn(Optional.of(overdueAlert(1L)));
        when(kpiDefinitionRepository.findById(20L)).thenReturn(Optional.of(kpi(3L)));

        assertThatThrownBy(() -> service.submitExplanation(1L, "Reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You can only explain Overdue alerts for your own committees.");
    }

    @Test
    void submitExplanation_rejectsAtRiskAlert() {
        givenUser("TBI_MANAGER", committee(3L));
        Alert atRisk = overdueAlert(1L);
        atRisk.setAlertType(Alert.TYPE_AT_RISK);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(atRisk));

        assertThatThrownBy(() -> service.submitExplanation(1L, "Reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Explanations can only be submitted for Overdue alerts.");
    }

    @Test
    void submitExplanation_rejectsMembers() {
        givenUser("STAFF", committee(3L));

        assertThatThrownBy(() -> service.submitExplanation(1L, "Reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only Committee Leads can explain Overdue alerts.");
    }

    // ---- helpers ----

    private User givenUser(String role, Committee committee) {
        User user = new User();
        user.setId(5L);
        user.setEmail("lead@example.com");
        user.setRole(role);
        user.setStatus("Active");
        user.setCommittees(List.of(committee));
        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(user));
        return user;
    }

    private Committee committee(Long id) {
        Committee committee = new Committee();
        committee.setId(id);
        committee.setName("Committee " + id);
        return committee;
    }

    private KpiDefinition kpi(Long committeeId) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(20L);
        kpi.setName("Revenue Target");
        kpi.setCommittee(committee(committeeId));
        return kpi;
    }

    private Alert overdueAlert(Long id) {
        Alert alert = new Alert();
        alert.setId(id);
        alert.setKpiDefinitionId(20L);
        alert.setAlertType(Alert.TYPE_OVERDUE);
        alert.setStatus(Alert.STATUS_UNACKNOWLEDGED);
        return alert;
    }
}
