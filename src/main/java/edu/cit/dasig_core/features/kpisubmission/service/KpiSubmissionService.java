package edu.cit.dasig_core.features.kpisubmission.service;

import edu.cit.dasig_core.core.event.KpiSubmittedEvent;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.util.ReportingPeriodResolver;
import edu.cit.dasig_core.features.kpisubmission.dto.CreateKpiSubmissionRequest;
import edu.cit.dasig_core.features.kpisubmission.dto.KpiSubmissionResponse;
import edu.cit.dasig_core.features.kpisubmission.dto.ReviewKpiSubmissionRequest;
import edu.cit.dasig_core.features.kpisubmission.dto.SubmissionDocumentResponse;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionDocument;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.SubmissionDocumentRepository;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPeriodProgressCalculator;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPeriodProgressCalculator.KpiPeriodProgress;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class KpiSubmissionService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final KpiAssignmentService kpiAssignmentService;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final SubmissionDocumentRepository submissionDocumentRepository;
    private final SubmissionDocumentService submissionDocumentService;
    private final ApplicationEventPublisher eventPublisher;

    public KpiSubmissionService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            KpiAssignmentService kpiAssignmentService,
            KpiSubmissionRepository kpiSubmissionRepository,
            SubmissionDocumentRepository submissionDocumentRepository,
            SubmissionDocumentService submissionDocumentService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.kpiAssignmentService = kpiAssignmentService;
        this.kpiSubmissionRepository = kpiSubmissionRepository;
        this.submissionDocumentRepository = submissionDocumentRepository;
        this.submissionDocumentService = submissionDocumentService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<KpiDefinition> getAssignedKpisForCurrentUser() {
        User user = resolveCurrentUser();
        validateSubmitterRole(user);
        return kpiAssignmentService.getAssignedKpis(user.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public List<KpiSubmissionResponse> getSubmissionsForCurrentUser(
            Long kpiDefinitionId,
            String reportingPeriod,
            SubmissionType submissionType,
            SubmissionReviewStatus reviewStatus
    ) {
        User user = resolveCurrentUser();
        validateSubmitterRole(user);

        return kpiSubmissionRepository
                .findByOrganizationIdOrderByDateCreatedDesc(user.getOrganizationId())
                .stream()
                .filter(submission -> matchesRoleVisibility(user, submission, submissionType))
                .filter(submission -> kpiDefinitionId == null
                        || submission.getKpiDefinition().getId().equals(kpiDefinitionId))
                .filter(submission -> reportingPeriod == null
                        || submission.getReportingPeriod().equalsIgnoreCase(reportingPeriod))
                .filter(submission -> submissionType == null
                        || submission.getSubmissionType() == submissionType)
                .filter(submission -> reviewStatus == null
                        || submission.getReviewStatus() == reviewStatus)
                .map(this::toResponse)
                .toList();
    }

    private boolean matchesRoleVisibility(User user, KpiSubmission submission, SubmissionType requestedSubmissionType) {
        if ("STAFF".equals(user.getRole())) {
            return requestedSubmissionType == SubmissionType.FINAL
                    ? submission.getSubmissionType() == SubmissionType.FINAL
                    : submission.getSubmissionType() == SubmissionType.INTERNAL;
        }
        return true;
    }

    @Transactional
    public KpiSubmissionResponse createSubmission(CreateKpiSubmissionRequest request, List<MultipartFile> files) {
        User user = resolveCurrentUser();
        validateSubmitterRole(user);

        KpiDefinition kpiDefinition = kpiAssignmentService.getAssignedKpi(
                request.getKpiDefinitionId(),
                user.getOrganizationId()
        );

        SubmissionType submissionType = resolveSubmissionType(user);

        LocalDate assignmentStart = kpiDefinition.getDateCreated() != null
                ? kpiDefinition.getDateCreated().toLocalDate()
                : kpiDefinition.getDeadline();
        if (!ReportingPeriodResolver.isValidPeriod(
                kpiDefinition.getReportingFrequency(),
                kpiDefinition.getDeadline(),
                assignmentStart,
                request.getReportingPeriod()
        )) {
            throw new IllegalArgumentException("Invalid reporting period for this KPI.");
        }

        List<KpiSubmission> relatedSubmissions = kpiSubmissionRepository
                .findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(
                        request.getKpiDefinitionId(),
                        user.getOrganizationId(),
                        submissionType
                );
        KpiPeriodProgress progress = KpiPeriodProgressCalculator.calculateWithNewSubmission(
                kpiDefinition,
                request.getReportingPeriod(),
                filterCountableSubmissions(relatedSubmissions),
                request.getSubmittedValue()
        );

        double achievementRate = progress.achievementRate();
        String performanceStatus = progress.performanceStatus();

        Organization userOrganization = organizationRepository.findById(user.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with ID: " + user.getOrganizationId()));

        KpiSubmission submission = new KpiSubmission();
        submission.setKpiDefinition(kpiDefinition);
        submission.setOrganization(userOrganization);
        submission.setSubmittedBy(user);
        submission.setSubmittedValue(request.getSubmittedValue());
        submission.setReportingPeriod(request.getReportingPeriod());
        submission.setSubmissionDate(request.getSubmissionDate());
        submission.setNotes(request.getNotes());
        submission.setSubmissionType(submissionType);
        submission.setAchievementRate(achievementRate);
        submission.setPerformanceStatus(performanceStatus);
        submission.setReviewStatus(resolveInitialReviewStatus(user));
        if (submission.getReviewStatus() == SubmissionReviewStatus.APPROVED) {
            submission.setReviewedBy(user);
            submission.setReviewedAt(LocalDateTime.now());
        }

        KpiSubmission savedSubmission = kpiSubmissionRepository.save(submission);

        submissionDocumentService.storeDocuments(savedSubmission, files);

        eventPublisher.publishEvent(new KpiSubmittedEvent(
                savedSubmission.getId(),
                BigDecimal.valueOf(savedSubmission.getSubmittedValue())
        ));

        return toResponse(savedSubmission);
    }

    @Transactional
    public KpiSubmissionResponse reviewSubmission(Long submissionId, ReviewKpiSubmissionRequest request) {
        User user = resolveCurrentUser();
        validateReviewerRole(user);

        KpiSubmission submission = kpiSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found."));

        if (!user.getOrganizationId().equals(submission.getOrganization().getId())) {
            throw new IllegalArgumentException("You do not have access to review this submission.");
        }

        if (submission.getSubmissionType() != SubmissionType.INTERNAL) {
            throw new IllegalArgumentException("Only staff internal submissions can be reviewed.");
        }

        if (submission.getReviewStatus() != SubmissionReviewStatus.PENDING) {
            throw new IllegalArgumentException("Only pending submissions can be reviewed.");
        }

        if (request.getReviewStatus() == SubmissionReviewStatus.PENDING) {
            throw new IllegalArgumentException("Review status must be APPROVED or REJECTED.");
        }

        submission.setReviewStatus(request.getReviewStatus());
        submission.setReviewedBy(user);
        submission.setReviewedAt(LocalDateTime.now());

        if (request.getReviewStatus() == SubmissionReviewStatus.REJECTED) {
            if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
                throw new IllegalArgumentException("A rejection reason is required.");
            }
            submission.setRejectionReason(request.getRejectionReason().trim());
            return toResponse(kpiSubmissionRepository.save(submission));
        }

        submission.setRejectionReason(null);
        KpiSubmission approvedSubmission = kpiSubmissionRepository.save(submission);

        if (!kpiSubmissionRepository.existsBySourceSubmissionId(approvedSubmission.getId())) {
            KpiSubmission officialSubmission = createOfficialSubmissionFromApprovedStaffSubmission(
                    approvedSubmission,
                    user
            );
            eventPublisher.publishEvent(new KpiSubmittedEvent(
                    officialSubmission.getId(),
                    BigDecimal.valueOf(officialSubmission.getSubmittedValue())
            ));
        }

        return toResponse(approvedSubmission);
    }

    @Transactional(readOnly = true)
    public SubmissionDocumentDownload getDocumentForCurrentUser(Long documentId) {
        User user = resolveCurrentUser();
        validateSubmitterRole(user);

        SubmissionDocument document = submissionDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Submission document not found."));
        KpiSubmission submission = document.getSubmission();

        if (!user.getOrganizationId().equals(submission.getOrganization().getId())) {
            throw new IllegalArgumentException("You do not have access to this document.");
        }

        if ("STAFF".equals(user.getRole()) && submission.getSubmissionType() != SubmissionType.INTERNAL) {
            throw new IllegalArgumentException("You do not have access to this document.");
        }

        byte[] content = submissionDocumentService.downloadDocument(document);
        return new SubmissionDocumentDownload(
                document.getFileName(),
                document.getContentType(),
                content
        );
    }

    private KpiSubmissionResponse toResponse(KpiSubmission submission) {
        KpiSubmissionResponse response = new KpiSubmissionResponse();
        response.setId(submission.getId());
        response.setKpiDefinitionId(submission.getKpiDefinition().getId());
        response.setKpiName(submission.getKpiDefinition().getName());
        response.setSubmittedByName(submission.getSubmittedBy().getName());
        response.setSubmittedByRole(submission.getSubmittedBy().getRole());
        response.setReportingPeriod(submission.getReportingPeriod());
        response.setSubmittedValue(submission.getSubmittedValue());
        response.setSubmissionDate(submission.getSubmissionDate());
        response.setNotes(submission.getNotes());
        response.setSubmissionType(submission.getSubmissionType());
        response.setAchievementRate(submission.getAchievementRate());
        response.setPerformanceStatus(submission.getPerformanceStatus());
        response.setReviewStatus(submission.getReviewStatus());
        response.setRejectionReason(submission.getRejectionReason());
        response.setReviewedByName(submission.getReviewedBy() != null ? submission.getReviewedBy().getName() : null);
        response.setReviewedAt(submission.getReviewedAt());
        response.setSourceSubmissionId(submission.getSourceSubmission() != null ? submission.getSourceSubmission().getId() : null);
        response.setCreatedAt(submission.getDateCreated());
        response.setDocuments(submissionDocumentRepository.findBySubmissionId(submission.getId())
                .stream()
                .map(this::toDocumentResponse)
                .toList());
        return response;
    }

    private SubmissionDocumentResponse toDocumentResponse(SubmissionDocument document) {
        SubmissionDocumentResponse response = new SubmissionDocumentResponse();
        response.setId(document.getId());
        response.setFileName(document.getFileName());
        response.setFileSize(document.getFileSize());
        response.setContentType(document.getContentType());
        return response;
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        if (!"Active".equals(user.getStatus())) {
            throw new IllegalArgumentException("Account is not active.");
        }

        return user;
    }

    private void validateSubmitterRole(User user) {
        if (user.getOrganizationId() == null) {
            throw new IllegalArgumentException("Organization is required to submit KPI values.");
        }

        if (!"STAFF".equals(user.getRole()) && !"TBI_MANAGER".equals(user.getRole())) {
            throw new IllegalArgumentException("Only Staff and TBI Managers can submit KPI values.");
        }
    }

    private void validateReviewerRole(User user) {
        if (user.getOrganizationId() == null) {
            throw new IllegalArgumentException("Organization is required to review KPI submissions.");
        }

        if (!"TBI_MANAGER".equals(user.getRole())) {
            throw new IllegalArgumentException("Only TBI Managers can review staff submissions.");
        }
    }

    private SubmissionType resolveSubmissionType(User user) {
        if ("STAFF".equals(user.getRole())) {
            return SubmissionType.INTERNAL;
        }
        return SubmissionType.FINAL;
    }

    private SubmissionReviewStatus resolveInitialReviewStatus(User user) {
        if ("STAFF".equals(user.getRole())) {
            return SubmissionReviewStatus.PENDING;
        }
        return SubmissionReviewStatus.APPROVED;
    }

    private KpiSubmission createOfficialSubmissionFromApprovedStaffSubmission(
            KpiSubmission staffSubmission,
            User reviewer
    ) {
        List<KpiSubmission> relatedFinalSubmissions = kpiSubmissionRepository
                .findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(
                        staffSubmission.getKpiDefinition().getId(),
                        staffSubmission.getOrganization().getId(),
                        SubmissionType.FINAL
                );

        KpiPeriodProgress progress = KpiPeriodProgressCalculator.calculateWithNewSubmission(
                staffSubmission.getKpiDefinition(),
                staffSubmission.getReportingPeriod(),
                filterCountableSubmissions(relatedFinalSubmissions),
                staffSubmission.getSubmittedValue()
        );

        KpiSubmission officialSubmission = new KpiSubmission();
        officialSubmission.setKpiDefinition(staffSubmission.getKpiDefinition());
        officialSubmission.setOrganization(staffSubmission.getOrganization());
        officialSubmission.setSubmittedBy(reviewer);
        officialSubmission.setSubmittedValue(staffSubmission.getSubmittedValue());
        officialSubmission.setReportingPeriod(staffSubmission.getReportingPeriod());
        officialSubmission.setSubmissionDate(staffSubmission.getSubmissionDate());
        officialSubmission.setNotes(staffSubmission.getNotes());
        officialSubmission.setSubmissionType(SubmissionType.FINAL);
        officialSubmission.setAchievementRate(progress.achievementRate());
        officialSubmission.setPerformanceStatus(progress.performanceStatus());
        officialSubmission.setReviewStatus(SubmissionReviewStatus.APPROVED);
        officialSubmission.setReviewedBy(reviewer);
        officialSubmission.setReviewedAt(LocalDateTime.now());
        officialSubmission.setSourceSubmission(staffSubmission);

        return kpiSubmissionRepository.save(officialSubmission);
    }

    private List<KpiSubmission> filterCountableSubmissions(List<KpiSubmission> submissions) {
        return submissions.stream()
                .filter(submission -> submission.getReviewStatus() != SubmissionReviewStatus.REJECTED)
                .toList();
    }

    public record SubmissionDocumentDownload(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }
}
