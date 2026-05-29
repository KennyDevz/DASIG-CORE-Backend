package edu.cit.dasig_core.features.kpisubmission.service;

import edu.cit.dasig_core.core.event.KpiSubmittedEvent;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpisubmission.dto.CreateKpiSubmissionRequest;
import edu.cit.dasig_core.features.kpisubmission.dto.KpiSubmissionResponse;
import edu.cit.dasig_core.features.kpisubmission.dto.SubmissionDocumentResponse;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionDocument;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.repository.SubmissionDocumentRepository;
import edu.cit.dasig_core.features.kpisubmission.util.KpiAchievementCalculator;
import edu.cit.dasig_core.features.kpisubmission.util.PerformanceStatusClassifier;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Service
public class KpiSubmissionService {

    private final UserRepository userRepository;
    private final KpiAssignmentService kpiAssignmentService;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final SubmissionDocumentRepository submissionDocumentRepository;
    private final SubmissionDocumentService submissionDocumentService;
    private final ApplicationEventPublisher eventPublisher;

    public KpiSubmissionService(
            UserRepository userRepository,
            KpiAssignmentService kpiAssignmentService,
            KpiSubmissionRepository kpiSubmissionRepository,
            SubmissionDocumentRepository submissionDocumentRepository,
            SubmissionDocumentService submissionDocumentService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
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
            SubmissionType submissionType
    ) {
        User user = resolveCurrentUser();
        validateSubmitterRole(user);

        return kpiSubmissionRepository
                .findByKpiDefinitionOrganizationIdOrderByDateCreatedDesc(user.getOrganizationId())
                .stream()
                .filter(submission -> matchesRoleVisibility(user, submission))
                .filter(submission -> kpiDefinitionId == null
                        || submission.getKpiDefinition().getId().equals(kpiDefinitionId))
                .filter(submission -> reportingPeriod == null
                        || submission.getReportingPeriod().equalsIgnoreCase(reportingPeriod))
                .filter(submission -> submissionType == null
                        || submission.getSubmissionType() == submissionType)
                .map(this::toResponse)
                .toList();
    }

    private boolean matchesRoleVisibility(User user, KpiSubmission submission) {
        if ("STAFF".equals(user.getRole())) {
            return submission.getSubmissionType() == SubmissionType.INTERNAL;
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

        if (kpiSubmissionRepository.existsByKpiDefinitionIdAndReportingPeriodAndSubmissionType(
                request.getKpiDefinitionId(),
                request.getReportingPeriod(),
                submissionType
        )) {
            throw new IllegalArgumentException(
                    "A submission already exists for this KPI, reporting period, and submission type.");
        }

        double achievementRate = KpiAchievementCalculator.calculate(
                request.getSubmittedValue(),
                kpiDefinition.getTargetValue()
        );
        String performanceStatus = PerformanceStatusClassifier.classify(
                achievementRate,
                kpiDefinition.getThreshold()
        );

        KpiSubmission submission = new KpiSubmission();
        submission.setKpiDefinition(kpiDefinition);
        submission.setSubmittedBy(user);
        submission.setSubmittedValue(request.getSubmittedValue());
        submission.setReportingPeriod(request.getReportingPeriod());
        submission.setSubmissionDate(request.getSubmissionDate());
        submission.setNotes(request.getNotes());
        submission.setSubmissionType(submissionType);
        submission.setAchievementRate(achievementRate);
        submission.setPerformanceStatus(performanceStatus);

        KpiSubmission savedSubmission = kpiSubmissionRepository.save(submission);

        submissionDocumentService.storeDocuments(savedSubmission, files);

        eventPublisher.publishEvent(new KpiSubmittedEvent(
                savedSubmission.getId(),
                BigDecimal.valueOf(savedSubmission.getSubmittedValue())
        ));

        return toResponse(savedSubmission);
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

    private SubmissionType resolveSubmissionType(User user) {
        if ("STAFF".equals(user.getRole())) {
            return SubmissionType.INTERNAL;
        }
        return SubmissionType.FINAL;
    }
}
