package edu.cit.dasig_core.features.kpisubmission.service;

import edu.cit.dasig_core.core.event.KpiSubmittedEvent;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import edu.cit.dasig_core.features.kpi.util.ReportingPeriodResolver;
import edu.cit.dasig_core.features.kpisubmission.dto.CreateKpiSubmissionRequest;
import edu.cit.dasig_core.features.kpisubmission.dto.KpiSubmissionResponse;
import edu.cit.dasig_core.features.kpisubmission.dto.ReviewKpiSubmissionRequest;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionDocument;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.SubmissionDocumentRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KpiSubmissionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private KpiAssignmentService kpiAssignmentService;
    @Mock
    private KpiSubmissionRepository kpiSubmissionRepository;
    @Mock
    private SubmissionDocumentRepository submissionDocumentRepository;
    @Mock
    private SubmissionDocumentService submissionDocumentService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private KpiSubmissionService kpiSubmissionService;

    @BeforeEach
    void setUp() {
        kpiSubmissionService = new KpiSubmissionService(
                userRepository, organizationRepository, kpiAssignmentService,
                kpiSubmissionRepository, submissionDocumentRepository, submissionDocumentService, eventPublisher);
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
        user.setName("Test User");
        user.setRole(role);
        user.setOrganizationId(orgId);
        user.setStatus("Active");
        return user;
    }

    private KpiDefinition kpiOneTime(LocalDate deadline) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(1L);
        kpi.setName("KPI");
        kpi.setTargetValue(100.0);
        kpi.setThreshold(50.0);
        kpi.setUnit("count");
        kpi.setDeadline(deadline);
        kpi.setReportingFrequency(ReportingFrequency.ONE_TIME);
        Committee committee = new Committee();
        committee.setId(1L);
        kpi.setCommittee(committee);
        return kpi;
    }

    private String validPeriodFor(KpiDefinition kpi) {
        return ReportingPeriodResolver.generatePeriodOptions(
                kpi.getReportingFrequency(), kpi.getDeadline(), kpi.getDeadline()).get(0);
    }

    // ---- createSubmission ----

    @Test
    void createSubmission_throwsWhenNotAuthenticated() {
        CreateKpiSubmissionRequest request = new CreateKpiSubmissionRequest();

        assertThatThrownBy(() -> kpiSubmissionService.createSubmission(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authentication is required.");
    }

    @Test
    void createSubmission_throwsWhenUserHasNoOrganization() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", null)));

        assertThatThrownBy(() -> kpiSubmissionService.createSubmission(new CreateKpiSubmissionRequest(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization is required to submit KPI values.");
    }

    @Test
    void createSubmission_throwsWhenRoleIsAdmin() {
        authenticateAs("user@example.com");
        User admin = user("DASIG_ADMIN", 1L);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> kpiSubmissionService.createSubmission(new CreateKpiSubmissionRequest(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only Staff and TBI Managers can submit KPI values.");
    }

    @Test
    void createSubmission_throwsWhenReportingPeriodInvalid() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", 9L)));

        KpiDefinition kpi = kpiOneTime(LocalDate.now().plusMonths(3));
        when(kpiAssignmentService.getAssignedKpi(1L, 9L)).thenReturn(kpi);

        CreateKpiSubmissionRequest request = new CreateKpiSubmissionRequest();
        request.setKpiDefinitionId(1L);
        request.setReportingPeriod("Not a real period");
        request.setSubmittedValue(10.0);
        request.setSubmissionDate(LocalDate.now());

        assertThatThrownBy(() -> kpiSubmissionService.createSubmission(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid reporting period for this KPI.");
    }

    @Test
    void createSubmission_throwsWhenSubmissionDateIsBeforeToday() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", 9L)));

        KpiDefinition kpi = kpiOneTime(LocalDate.now().plusMonths(3));
        when(kpiAssignmentService.getAssignedKpi(1L, 9L)).thenReturn(kpi);

        CreateKpiSubmissionRequest request = new CreateKpiSubmissionRequest();
        request.setKpiDefinitionId(1L);
        request.setReportingPeriod(validPeriodFor(kpi));
        request.setSubmittedValue(10.0);
        request.setSubmissionDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> kpiSubmissionService.createSubmission(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Submission date cannot be before today.");
    }

    @Test
    void createSubmission_staffSubmissionIsInternalAndPending() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", 9L)));

        KpiDefinition kpi = kpiOneTime(LocalDate.now().plusMonths(3));
        when(kpiAssignmentService.getAssignedKpi(1L, 9L)).thenReturn(kpi);
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(any(), any(), any()))
                .thenReturn(List.of());
        Organization org = new Organization();
        org.setId(9L);
        when(organizationRepository.findById(9L)).thenReturn(Optional.of(org));
        when(kpiSubmissionRepository.save(any(KpiSubmission.class))).thenAnswer(invocation -> {
            KpiSubmission submission = invocation.getArgument(0);
            submission.setId(100L);
            return submission;
        });
        when(submissionDocumentRepository.findBySubmissionId(100L)).thenReturn(List.of());

        CreateKpiSubmissionRequest request = new CreateKpiSubmissionRequest();
        request.setKpiDefinitionId(1L);
        request.setReportingPeriod(validPeriodFor(kpi));
        request.setSubmittedValue(10.0);
        request.setSubmissionDate(LocalDate.now());

        KpiSubmissionResponse response = kpiSubmissionService.createSubmission(request, null);

        assertThat(response.getSubmissionType()).isEqualTo(SubmissionType.INTERNAL);
        assertThat(response.getReviewStatus()).isEqualTo(SubmissionReviewStatus.PENDING);
        verify(eventPublisher).publishEvent(any(KpiSubmittedEvent.class));
    }

    @Test
    void createSubmission_tbiManagerSubmissionIsFinalAndAutoApproved() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));

        KpiDefinition kpi = kpiOneTime(LocalDate.now().plusMonths(3));
        when(kpiAssignmentService.getAssignedKpi(1L, 9L)).thenReturn(kpi);
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(any(), any(), any()))
                .thenReturn(List.of());
        Organization org = new Organization();
        org.setId(9L);
        when(organizationRepository.findById(9L)).thenReturn(Optional.of(org));
        when(kpiSubmissionRepository.save(any(KpiSubmission.class))).thenAnswer(invocation -> {
            KpiSubmission submission = invocation.getArgument(0);
            submission.setId(101L);
            return submission;
        });
        when(submissionDocumentRepository.findBySubmissionId(101L)).thenReturn(List.of());

        CreateKpiSubmissionRequest request = new CreateKpiSubmissionRequest();
        request.setKpiDefinitionId(1L);
        request.setReportingPeriod(validPeriodFor(kpi));
        request.setSubmittedValue(10.0);
        request.setSubmissionDate(LocalDate.now());

        KpiSubmissionResponse response = kpiSubmissionService.createSubmission(request, null);

        assertThat(response.getSubmissionType()).isEqualTo(SubmissionType.FINAL);
        assertThat(response.getReviewStatus()).isEqualTo(SubmissionReviewStatus.APPROVED);
    }

    // ---- reviewSubmission ----

    private KpiSubmission internalPendingSubmission(Long orgId) {
        KpiSubmission submission = new KpiSubmission();
        submission.setId(1L);
        KpiDefinition kpi = kpiOneTime(LocalDate.now().plusMonths(1));
        submission.setKpiDefinition(kpi);
        Organization org = new Organization();
        org.setId(orgId);
        submission.setOrganization(org);
        submission.setSubmissionType(SubmissionType.INTERNAL);
        submission.setReviewStatus(SubmissionReviewStatus.PENDING);
        submission.setSubmittedValue(10.0);
        submission.setReportingPeriod(validPeriodFor(kpi));
        submission.setSubmissionDate(LocalDate.now());
        User submitter = user("STAFF", orgId);
        submission.setSubmittedBy(submitter);
        return submission;
    }

    @Test
    void reviewSubmission_throwsWhenNotTbiManager() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", 9L)));

        assertThatThrownBy(() -> kpiSubmissionService.reviewSubmission(1L, new ReviewKpiSubmissionRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only TBI Managers can review staff submissions.");
    }

    @Test
    void reviewSubmission_throwsWhenSubmissionNotFound() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));
        when(kpiSubmissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiSubmissionService.reviewSubmission(1L, new ReviewKpiSubmissionRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Submission not found.");
    }

    @Test
    void reviewSubmission_throwsWhenReviewerFromDifferentOrganization() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));
        when(kpiSubmissionRepository.findById(1L)).thenReturn(Optional.of(internalPendingSubmission(999L)));

        assertThatThrownBy(() -> kpiSubmissionService.reviewSubmission(1L, new ReviewKpiSubmissionRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You do not have access to review this submission.");
    }

    @Test
    void reviewSubmission_throwsWhenNotInternalType() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));

        KpiSubmission submission = internalPendingSubmission(9L);
        submission.setSubmissionType(SubmissionType.FINAL);
        when(kpiSubmissionRepository.findById(1L)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> kpiSubmissionService.reviewSubmission(1L, new ReviewKpiSubmissionRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only staff internal submissions can be reviewed.");
    }

    @Test
    void reviewSubmission_throwsWhenNotPending() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));

        KpiSubmission submission = internalPendingSubmission(9L);
        submission.setReviewStatus(SubmissionReviewStatus.APPROVED);
        when(kpiSubmissionRepository.findById(1L)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> kpiSubmissionService.reviewSubmission(1L, new ReviewKpiSubmissionRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only pending submissions can be reviewed.");
    }

    @Test
    void reviewSubmission_throwsWhenRejectedWithoutReason() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));
        when(kpiSubmissionRepository.findById(1L)).thenReturn(Optional.of(internalPendingSubmission(9L)));

        ReviewKpiSubmissionRequest request = new ReviewKpiSubmissionRequest();
        request.setReviewStatus(SubmissionReviewStatus.REJECTED);
        request.setRejectionReason("  ");

        assertThatThrownBy(() -> kpiSubmissionService.reviewSubmission(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A rejection reason is required.");
    }

    @Test
    void reviewSubmission_rejectsWithReasonAndDoesNotCreateOfficialSubmission() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));
        KpiSubmission submission = internalPendingSubmission(9L);
        when(kpiSubmissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(kpiSubmissionRepository.save(any(KpiSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionDocumentRepository.findBySubmissionId(any())).thenReturn(List.of());

        ReviewKpiSubmissionRequest request = new ReviewKpiSubmissionRequest();
        request.setReviewStatus(SubmissionReviewStatus.REJECTED);
        request.setRejectionReason("Insufficient evidence");

        KpiSubmissionResponse response = kpiSubmissionService.reviewSubmission(1L, request);

        assertThat(response.getReviewStatus()).isEqualTo(SubmissionReviewStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Insufficient evidence");
        verify(kpiSubmissionRepository, never()).existsBySourceSubmissionId(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reviewSubmission_approvingCreatesOfficialFinalSubmissionAndPublishesEvent() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));
        KpiSubmission submission = internalPendingSubmission(9L);
        when(kpiSubmissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(kpiSubmissionRepository.save(any(KpiSubmission.class))).thenAnswer(invocation -> {
            KpiSubmission s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(200L);
            }
            return s;
        });
        when(kpiSubmissionRepository.existsBySourceSubmissionId(1L)).thenReturn(false);
        when(kpiSubmissionRepository.findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(any(), any(), any()))
                .thenReturn(List.of());
        when(submissionDocumentRepository.findBySubmissionId(any())).thenReturn(List.of());

        ReviewKpiSubmissionRequest request = new ReviewKpiSubmissionRequest();
        request.setReviewStatus(SubmissionReviewStatus.APPROVED);

        KpiSubmissionResponse response = kpiSubmissionService.reviewSubmission(1L, request);

        assertThat(response.getReviewStatus()).isEqualTo(SubmissionReviewStatus.APPROVED);
        verify(eventPublisher).publishEvent(any(KpiSubmittedEvent.class));
    }

    @Test
    void reviewSubmission_approvingDoesNotDuplicateOfficialSubmissionIfOneAlreadyExists() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("TBI_MANAGER", 9L)));
        KpiSubmission submission = internalPendingSubmission(9L);
        when(kpiSubmissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(kpiSubmissionRepository.save(any(KpiSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kpiSubmissionRepository.existsBySourceSubmissionId(1L)).thenReturn(true);
        when(submissionDocumentRepository.findBySubmissionId(any())).thenReturn(List.of());

        ReviewKpiSubmissionRequest request = new ReviewKpiSubmissionRequest();
        request.setReviewStatus(SubmissionReviewStatus.APPROVED);

        kpiSubmissionService.reviewSubmission(1L, request);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- getDocumentForCurrentUser ----

    @Test
    void getDocumentForCurrentUser_throwsWhenDocumentNotFound() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", 9L)));
        when(submissionDocumentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiSubmissionService.getDocumentForCurrentUser(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Submission document not found.");
    }

    @Test
    void getDocumentForCurrentUser_throwsWhenDifferentOrganization() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", 9L)));

        SubmissionDocument document = new SubmissionDocument();
        document.setId(1L);
        document.setSubmission(internalPendingSubmission(999L));
        when(submissionDocumentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> kpiSubmissionService.getDocumentForCurrentUser(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You do not have access to this document.");
    }

    @Test
    void getDocumentForCurrentUser_staffCannotAccessFinalTypeDocuments() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", 9L)));

        KpiSubmission submission = internalPendingSubmission(9L);
        submission.setSubmissionType(SubmissionType.FINAL);
        SubmissionDocument document = new SubmissionDocument();
        document.setId(1L);
        document.setSubmission(submission);
        when(submissionDocumentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> kpiSubmissionService.getDocumentForCurrentUser(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You do not have access to this document.");
    }

    @Test
    void getDocumentForCurrentUser_returnsDownloadOnSuccess() {
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user("STAFF", 9L)));

        SubmissionDocument document = new SubmissionDocument();
        document.setId(1L);
        document.setFileName("report.pdf");
        document.setContentType("application/pdf");
        document.setSubmission(internalPendingSubmission(9L));
        when(submissionDocumentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(submissionDocumentService.downloadDocument(document)).thenReturn(new byte[]{1, 2, 3});

        KpiSubmissionService.SubmissionDocumentDownload download = kpiSubmissionService.getDocumentForCurrentUser(1L);

        assertThat(download.fileName()).isEqualTo("report.pdf");
        assertThat(download.content()).containsExactly(1, 2, 3);
    }
}
