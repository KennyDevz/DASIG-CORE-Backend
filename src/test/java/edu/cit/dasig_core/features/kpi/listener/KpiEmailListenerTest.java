package edu.cit.dasig_core.features.kpi.listener;

import edu.cit.dasig_core.core.event.KpiEmailEvent;
import edu.cit.dasig_core.core.smtp.EmailService;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiEmailListenerTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 15);

    @Mock
    private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private KpiSubmissionRepository kpiSubmissionRepository;

    private KpiEmailListener listener;
    private Committee committee;

    @BeforeEach
    void setUp() {
        listener = new KpiEmailListener(kpiDefinitionRepository, userRepository, emailService, kpiSubmissionRepository);

        Organization alpha = new Organization();
        alpha.setId(20L);
        Organization beta = new Organization();
        beta.setId(21L);
        committee = new Committee();
        committee.setId(5L);
        committee.setName("Committee A");
        committee.setOrganizations(List.of(alpha, beta));
    }

    @Test
    void kpiAssigned_emailsActiveLeadsAndMembersOfTheCommittee() {
        givenKpi();
        User lead = user(1L, "lead@test.com", "TBI_MANAGER");
        User member = user(2L, "member@gmail.com", "STAFF");
        when(userRepository.findByRoleAndStatusAndCommittees_Id("TBI_MANAGER", "Active", 5L)).thenReturn(List.of(lead));
        when(userRepository.findByRoleAndStatusAndOrganizationIdIn("STAFF", "Active", List.of(20L, 21L))).thenReturn(List.of(member));

        listener.onKpiEmailEvent(KpiEmailEvent.assigned(10L));

        verify(emailService).sendKpiAssignedEmail("lead@test.com", "lead@test.com", "TBI_MANAGER", 10L,
                "Startups Incubated", "Committee A", "100 startups", DEADLINE);
        verify(emailService).sendKpiAssignedEmail("member@gmail.com", "member@gmail.com", "STAFF", 10L,
                "Startups Incubated", "Committee A", "100 startups", DEADLINE);
    }

    @Test
    void deadlineUpdated_sendsOldAndNewDatesAndRemark() {
        givenKpi();
        User member = user(2L, "member@gmail.com", "STAFF");
        when(userRepository.findByRoleAndStatusAndOrganizationIdIn("STAFF", "Active", List.of(20L, 21L))).thenReturn(List.of(member));
        LocalDate oldDeadline = LocalDate.of(2026, 11, 1);

        listener.onKpiEmailEvent(KpiEmailEvent.deadlineUpdated(10L, oldDeadline, DEADLINE, "Extended after review."));

        verify(emailService).sendKpiDeadlineUpdatedEmail("member@gmail.com", "member@gmail.com", "STAFF", 10L,
                "Startups Incubated", "Committee A", oldDeadline, DEADLINE, "Extended after review.");
        verify(emailService, never()).sendKpiAssignedEmail(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void findRecipients_removesDuplicateEmailsAndUsersWithoutEmail() {
        User lead = user(1L, "Same@Test.com", "TBI_MANAGER");
        User sameEmail = user(2L, "same@test.com", "STAFF");
        User noEmail = user(3L, " ", "STAFF");
        when(userRepository.findByRoleAndStatusAndCommittees_Id("TBI_MANAGER", "Active", 5L)).thenReturn(List.of(lead));
        when(userRepository.findByRoleAndStatusAndOrganizationIdIn("STAFF", "Active", List.of(20L, 21L)))
                .thenReturn(List.of(sameEmail, noEmail));

        assertThat(listener.findRecipients(committee)).containsExactly(lead);
    }

    @Test
    void findRecipients_skipsMemberLookup_whenCommitteeHasNoOrganizations() {
        committee.setOrganizations(List.of());
        when(userRepository.findByRoleAndStatusAndCommittees_Id("TBI_MANAGER", "Active", 5L)).thenReturn(List.of());

        assertThat(listener.findRecipients(committee)).isEmpty();
        verify(userRepository, never()).findByRoleAndStatusAndOrganizationIdIn(anyString(), anyString(), any());
    }

    @Test
    void missingKpi_sendsNothing() {
        when(kpiDefinitionRepository.findById(99L)).thenReturn(Optional.empty());

        listener.onKpiEmailEvent(KpiEmailEvent.assigned(99L));

        verifyNoInteractions(emailService, userRepository);
    }

    @Test
    void formatTarget_dropsDecimalForWholeNumbers() {
        assertThat(KpiEmailListener.formatTarget(100.0, "startups")).isEqualTo("100 startups");
        assertThat(KpiEmailListener.formatTarget(12.5, "%")).isEqualTo("12.5 %");
        assertThat(KpiEmailListener.formatTarget(5.0, null)).isEqualTo("5");
    }

    @Test
    void kpiAtRisk_emailsActiveLeadsAndMembersOfTheCommittee() {
        givenKpi();
        User lead = user(1L, "lead@test.com", "TBI_MANAGER");
        User member = user(2L, "member@gmail.com", "STAFF");
        when(userRepository.findByRoleAndStatusAndCommittees_Id("TBI_MANAGER", "Active", 5L)).thenReturn(List.of(lead));
        when(userRepository.findByRoleAndStatusAndOrganizationIdIn("STAFF", "Active", List.of(20L, 21L))).thenReturn(List.of(member));

        listener.onKpiEmailEvent(KpiEmailEvent.alertCreated(10L, "AT_RISK"));

        verify(emailService).sendKpiAlertCommitteeEmail("lead@test.com", "lead@test.com", "TBI_MANAGER",
                "Startups Incubated", "Committee A", false, "100 startups", DEADLINE);
        verify(emailService).sendKpiAlertCommitteeEmail("member@gmail.com", "member@gmail.com", "STAFF",
                "Startups Incubated", "Committee A", false, "100 startups", DEADLINE);
    }

    @Test
    void kpiOverdue_marksTheEmailAsOverdue() {
        givenKpi();
        User member = user(2L, "member@gmail.com", "STAFF");
        when(userRepository.findByRoleAndStatusAndOrganizationIdIn("STAFF", "Active", List.of(20L, 21L))).thenReturn(List.of(member));

        listener.onKpiEmailEvent(KpiEmailEvent.alertCreated(10L, "OVERDUE"));

        verify(emailService).sendKpiAlertCommitteeEmail("member@gmail.com", "member@gmail.com", "STAFF",
                "Startups Incubated", "Committee A", true, "100 startups", DEADLINE);
    }

    // ---- Submission emails ----

    @Test
    void submissionSent_emailsActiveLeadsOfTheCommittee() {
        KpiSubmission submission = givenSubmission(SubmissionReviewStatus.PENDING);
        User lead = user(1L, "lead@test.com", "TBI_MANAGER");
        when(userRepository.findByRoleAndStatusAndCommittees_Id("TBI_MANAGER", "Active", 5L)).thenReturn(List.of(lead));

        listener.onKpiEmailEvent(KpiEmailEvent.submissionSent(50L));

        verify(emailService).sendSubmissionForReviewEmail("lead@test.com", "lead@test.com", "Startups Incubated",
                "TBI Alpha", "member@gmail.com", "40 startups", submission.getReportingPeriod(),
                submission.getSubmissionDate(), "SUB-0050");
        verify(userRepository, never()).findByRoleAndStatusAndOrganizationIdIn(anyString(), anyString(), any());
    }

    @Test
    void submissionReviewed_emailsTheSubmittingMemberWithTheReason() {
        KpiSubmission submission = givenSubmission(SubmissionReviewStatus.REJECTED);
        submission.setRejectionReason("Missing evidence");
        submission.setReviewedBy(user(1L, "lead@test.com", "TBI_MANAGER"));

        listener.onKpiEmailEvent(KpiEmailEvent.submissionReviewed(50L));

        verify(emailService).sendSubmissionReviewedEmail("member@gmail.com", "member@gmail.com", "Startups Incubated",
                "40 startups", "SUB-0050", false, "lead@test.com", "Missing evidence");
    }

    @Test
    void submissionReviewed_skipsInactiveMember() {
        KpiSubmission submission = givenSubmission(SubmissionReviewStatus.APPROVED);
        submission.getSubmittedBy().setStatus("Inactive");

        listener.onKpiEmailEvent(KpiEmailEvent.submissionReviewed(50L));

        verifyNoInteractions(emailService);
    }

    @Test
    void finalValueApproved_emailsActiveAdmins() {
        KpiSubmission submission = givenSubmission(SubmissionReviewStatus.APPROVED);
        submission.setReviewedBy(user(1L, "lead@test.com", "TBI_MANAGER"));
        User admin = user(3L, "admin@gmail.com", "DASIG_ADMIN");
        when(userRepository.findByRoleAndStatus("DASIG_ADMIN", "Active")).thenReturn(List.of(admin));

        listener.onKpiEmailEvent(KpiEmailEvent.finalValueApproved(50L));

        verify(emailService).sendFinalValueApprovedEmail("admin@gmail.com", "admin@gmail.com", 10L, "Startups Incubated",
                "Committee A", "TBI Alpha", "40 startups", submission.getReportingPeriod(), "lead@test.com");
    }

    @Test
    void missingSubmission_sendsNothing() {
        when(kpiSubmissionRepository.findById(99L)).thenReturn(Optional.empty());

        listener.onKpiEmailEvent(KpiEmailEvent.submissionSent(99L));

        verifyNoInteractions(emailService, userRepository);
    }

    private KpiSubmission givenSubmission(SubmissionReviewStatus status) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(10L);
        kpi.setName("Startups Incubated");
        kpi.setUnit("startups");
        kpi.setCommittee(committee);
        Organization organization = new Organization();
        organization.setId(20L);
        organization.setName("TBI Alpha");

        KpiSubmission submission = new KpiSubmission();
        submission.setId(50L);
        submission.setKpiDefinition(kpi);
        submission.setOrganization(organization);
        submission.setSubmittedBy(user(2L, "member@gmail.com", "STAFF"));
        submission.setSubmittedValue(40.0);
        submission.setReportingPeriod("Due by Dec 15, 2026");
        submission.setSubmissionDate(LocalDate.of(2026, 10, 20));
        submission.setReferenceCode("SUB-0050");
        submission.setReviewStatus(status);
        when(kpiSubmissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        return submission;
    }

    private void givenKpi() {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(10L);
        kpi.setName("Startups Incubated");
        kpi.setTargetValue(100.0);
        kpi.setUnit("startups");
        kpi.setDeadline(DEADLINE);
        kpi.setCommittee(committee);
        when(kpiDefinitionRepository.findById(10L)).thenReturn(Optional.of(kpi));
    }

    private User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setStatus("Active");
        return user;
    }
}
