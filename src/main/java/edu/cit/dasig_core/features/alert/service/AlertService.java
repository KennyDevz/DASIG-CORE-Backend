package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.features.alert.dto.AlertDetailResponse;
import edu.cit.dasig_core.features.alert.dto.AlertResponse;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.alert.repository.OverdueExplanationRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPaceCalculator;
import edu.cit.dasig_core.features.notification.service.NotificationService;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final UserRepository userRepository;
    private final KpiAlertEvaluatorService kpiAlertEvaluatorService;
    private final NotificationService notificationService;
    private final OverdueExplanationRepository overdueExplanationRepository;

    @Value("${app.business-timezone:Asia/Manila}")
    private String businessTimezone;

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessTimezone));
    }

    @Transactional
    public List<AlertResponse> getAllAlerts() {
        User user = resolveCurrentUser();
        validateAlertViewerRole(user);

        // Run evaluator on-demand so newly overdue / at-risk KPIs appear immediately
        kpiAlertEvaluatorService.evaluateAllKpiAlerts();

        List<Alert> alerts = alertRepository.findAllByOrderByDetectedAtDesc();
        return alerts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AlertDetailResponse getAlertById(Long id) {
        User user = resolveCurrentUser();
        validateAlertViewerRole(user);

        return toDetail(findAlert(id));
    }

    /**
     * Admin acknowledges an open alert with a short remark (UC-4.2).
     * Resolved alerts cannot be acknowledged.
     */
    @Transactional
    public AlertDetailResponse acknowledgeAlert(Long id, String remark) {
        User admin = resolveCurrentUser();
        validateAlertViewerRole(admin);

        Alert alert = findAlert(id);
        if (alert.isResolved()) {
            throw new IllegalArgumentException("Resolved alerts cannot be acknowledged.");
        }
        if (Alert.STATUS_ACKNOWLEDGED.equals(alert.getStatus())) {
            throw new IllegalArgumentException("Alert is already acknowledged.");
        }

        alert.setStatus(Alert.STATUS_ACKNOWLEDGED);
        alert.setAdminRemark(remark.trim());
        alert.setRemarkedById(admin.getId());
        alert.setRemarkedAt(LocalDateTime.now(ZoneId.of(businessTimezone)));

        Alert saved = alertRepository.save(alert);
        AlertDetailResponse response = toDetail(saved);

        // Let the committee's organizations see the Admin's remark (UC-4.3)
        kpiDefinitionRepository.findById(response.getKpiDefinitionId())
                .ifPresent(kpi -> notificationService.notifyAlertAcknowledged(kpi, saved));

        return response;
    }

    public boolean existsForSubmission(Long submissionId) {
        return alertRepository.existsBySubmissionId(submissionId);
    }

    private void validateAlertViewerRole(User user) {
        if (!"DASIG_ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Only DASIG Admins can view alerts.");
        }
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        if (!"Active".equals(user.getStatus())) {
            throw new IllegalArgumentException("Account is not active.");
        }

        return user;
    }

    private Alert findAlert(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found with ID: " + id));
    }

    private AlertDetailResponse toDetail(Alert alert) {
        // submissionId is only set on legacy alerts; new alerts are KPI-level
        KpiSubmission submission = alert.getSubmissionId() != null
                ? kpiSubmissionRepository.findById(alert.getSubmissionId()).orElse(null)
                : null;

        KpiDefinition kpi = submission != null
                ? submission.getKpiDefinition()
                : kpiDefinitionRepository.findById(alert.getKpiDefinitionId())
                        .orElseThrow(() -> new IllegalArgumentException("KPI not found for alert with ID: " + alert.getId()));

        return toDetailResponse(alert, submission, kpi);
    }

    /** "Awaiting explanation" or "Explained" for Overdue alerts; null for At Risk alerts. */
    private String explanationStatus(Alert alert) {
        if (!Alert.TYPE_OVERDUE.equals(alert.getAlertType())) {
            return null;
        }
        return overdueExplanationRepository.existsByAlertId(alert.getId())
                ? OverdueExplanationService.STATUS_EXPLAINED
                : OverdueExplanationService.STATUS_AWAITING;
    }

    private AlertResponse toResponse(Alert alert) {
        AlertResponse response = new AlertResponse();
        response.setId(alert.getId());
        response.setSubmissionId(alert.getSubmissionId());
        response.setKpiDefinitionId(alert.getKpiDefinitionId());
        response.setAlertType(alert.getAlertType());
        response.setSeverity(alert.getSeverity());
        response.setStatus(alert.getStatus());
        response.setDetectedAt(alert.getDetectedAt());
        response.setResolvedAt(alert.getResolvedAt());
        response.setResolutionReason(alert.getResolutionReason());
        response.setExplanationStatus(explanationStatus(alert));
        return response;
    }

    private AlertDetailResponse toDetailResponse(Alert alert, KpiSubmission submission, KpiDefinition kpi) {
        AlertDetailResponse response = new AlertDetailResponse();
        response.setId(alert.getId());
        response.setSubmissionId(alert.getSubmissionId());
        response.setKpiDefinitionId(kpi.getId());
        response.setKpiName(kpi.getName());
        response.setAlertType(alert.getAlertType());
        response.setSeverity(alert.getSeverity());
        response.setStatus(alert.getStatus());
        response.setDetectedAt(alert.getDetectedAt());
        response.setDeadline(kpi.getDeadline());

        if (kpi.getDeadline() != null) {
            response.setDaysUntilDeadline(ChronoUnit.DAYS.between(today(), kpi.getDeadline()));
        }

        if (kpi.getCommittee() != null) {
            response.setCommitteeName(kpi.getCommittee().getName());
        }

        // Fetch cumulative final submissions for this KPI
        List<KpiSubmission> kpiHistory = kpiSubmissionRepository
                .findByKpiDefinitionIdAndSubmissionType(kpi.getId(), SubmissionType.FINAL);

        double cumulativeValue = kpiHistory.stream()
                .filter(s -> s.getReviewStatus() == null || s.getReviewStatus() == SubmissionReviewStatus.APPROVED)
                .mapToDouble(s -> s.getSubmittedValue() != null ? s.getSubmittedValue() : 0.0)
                .sum();

        double target = kpi.getTargetValue() != null ? kpi.getTargetValue() : 0.0;
        double achievementRate = target > 0 ? (cumulativeValue / target) * 100.0 : 0.0;

        response.setCumulativeValue(cumulativeValue);
        response.setScaledPeriodTarget(target);
        response.setAchievementRate(achievementRate);

        LocalDate startDate = kpi.getDateCreated() != null ? kpi.getDateCreated().toLocalDate() : null;
        response.setExpectedByNow(KpiPaceCalculator.expectedByNow(target, startDate, kpi.getDeadline(), today()));

        // Lifecycle: Admin remark and automatic resolution
        response.setAdminRemark(alert.getAdminRemark());
        response.setRemarkedAt(alert.getRemarkedAt());
        if (alert.getRemarkedById() != null) {
            userRepository.findById(alert.getRemarkedById())
                    .ifPresent(admin -> response.setRemarkedByName(admin.getName()));
        }
        response.setResolvedAt(alert.getResolvedAt());
        response.setResolutionReason(alert.getResolutionReason());

        // Committee Lead's explanation (Overdue alerts only, UC-4.4)
        response.setExplanationStatus(explanationStatus(alert));
        overdueExplanationRepository.findByAlertId(alert.getId()).ifPresent(explanation -> {
            response.setExplanation(explanation.getExplanation());
            response.setExplainedAt(explanation.getCreatedAt());
            userRepository.findById(explanation.getAuthorId())
                    .ifPresent(author -> response.setExplainedByName(author.getName()));
        });

        if (submission != null) {
            response.setReportingPeriod(submission.getReportingPeriod());
            response.setPeriodContribution(submission.getSubmittedValue());
            response.setSubmissionDate(submission.getSubmissionDate());
            response.setPerformanceStatus(submission.getPerformanceStatus());
            response.setSubmissionType(submission.getSubmissionType());
        } else {
            response.setReportingPeriod(alert.getAlertType() != null ? alert.getAlertType() : "OVERDUE");
            response.setPeriodContribution(0.0);
            response.setPerformanceStatus(Alert.TYPE_OVERDUE.equals(alert.getAlertType()) ? "OVERDUE" : "AT_RISK");
        }

        return response;
    }
}