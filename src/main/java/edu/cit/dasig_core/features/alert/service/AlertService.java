package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.features.alert.dto.AlertDetailResponse;
import edu.cit.dasig_core.features.alert.dto.AlertResponse;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.model.OverdueExplanation;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.alert.repository.OverdueExplanationRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** All alerts with their details in one response (Alerts page), newest first. */
    @Transactional
    public List<AlertDetailResponse> getAllAlertDetails() {
        User user = resolveCurrentUser();
        validateAlertViewerRole(user);

        // Run evaluator on-demand so newly overdue / at-risk KPIs appear immediately
        kpiAlertEvaluatorService.evaluateAllKpiAlerts();

        return toDetails(alertRepository.findAllByOrderByDetectedAtDesc());
    }

    /** Number of unacknowledged alerts for the sidebar badge (no evaluation, one count query). */
    @Transactional(readOnly = true)
    public long countUnacknowledgedAlerts() {
        User user = resolveCurrentUser();
        validateAlertViewerRole(user);

        return alertRepository.countByStatus(Alert.STATUS_UNACKNOWLEDGED);
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
        List<AlertDetailResponse> details = toDetails(List.of(alert));
        if (details.isEmpty()) {
            throw new IllegalArgumentException("KPI not found for alert with ID: " + alert.getId());
        }
        return details.get(0);
    }

    /**
     * Builds alert details with a fixed number of queries, however many alerts there are.
     * Alerts whose KPI no longer exists are skipped.
     */
    private List<AlertDetailResponse> toDetails(List<Alert> alerts) {
        // submissionId is only set on legacy alerts; new alerts are KPI-level
        Map<Long, KpiSubmission> legacySubmissions = kpiSubmissionRepository
                .findAllById(idsOf(alerts, Alert::getSubmissionId)).stream()
                .collect(Collectors.toMap(KpiSubmission::getId, submission -> submission));
        Map<Long, KpiDefinition> kpis = kpiDefinitionRepository
                .findAllById(idsOf(alerts, Alert::getKpiDefinitionId)).stream()
                .collect(Collectors.toMap(KpiDefinition::getId, kpi -> kpi));
        Map<Long, OverdueExplanation> explanations = overdueExplanationRepository
                .findByAlertIdIn(idsOf(alerts, Alert::getId)).stream()
                .collect(Collectors.toMap(OverdueExplanation::getAlertId, explanation -> explanation));

        // Names of the Admins who remarked and the Leads who explained
        Set<Long> userIds = idsOf(alerts, Alert::getRemarkedById);
        userIds.addAll(idsOf(explanations.values(), OverdueExplanation::getAuthorId));
        Map<Long, String> userNames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        // KPI of each alert (legacy alerts take it from their submission)
        List<KpiSubmission> alertSubmissions = new ArrayList<>();
        List<KpiDefinition> alertKpis = new ArrayList<>();
        for (Alert alert : alerts) {
            KpiSubmission submission = legacySubmissions.get(alert.getSubmissionId());
            alertSubmissions.add(submission);
            alertKpis.add(submission != null ? submission.getKpiDefinition() : kpis.get(alert.getKpiDefinitionId()));
        }
        Map<Long, Double> totals = kpiAlertEvaluatorService.approvedTotals(idsOf(alertKpis, KpiDefinition::getId));

        List<AlertDetailResponse> details = new ArrayList<>();
        for (int i = 0; i < alerts.size(); i++) {
            Alert alert = alerts.get(i);
            KpiDefinition kpi = alertKpis.get(i);
            if (kpi != null) {
                details.add(toDetailResponse(alert, alertSubmissions.get(i), kpi,
                        totals.getOrDefault(kpi.getId(), 0.0), explanations.get(alert.getId()), userNames));
            }
        }
        return details;
    }

    /** Distinct non-null IDs taken from a list of records. */
    private static <T> Set<Long> idsOf(Collection<T> items, Function<T, Long> getId) {
        return items.stream()
                .filter(Objects::nonNull)
                .map(getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** "Awaiting explanation" or "Explained" for Overdue alerts; null for At Risk alerts. */
    private String explanationStatus(Alert alert, boolean explained) {
        if (!Alert.TYPE_OVERDUE.equals(alert.getAlertType())) {
            return null;
        }
        return explained ? OverdueExplanationService.STATUS_EXPLAINED : OverdueExplanationService.STATUS_AWAITING;
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
        boolean overdue = Alert.TYPE_OVERDUE.equals(alert.getAlertType());
        response.setExplanationStatus(explanationStatus(alert, overdue && overdueExplanationRepository.existsByAlertId(alert.getId())));
        return response;
    }

    private AlertDetailResponse toDetailResponse(Alert alert, KpiSubmission submission, KpiDefinition kpi,
                                                 double cumulativeValue, OverdueExplanation explanation,
                                                 Map<Long, String> userNames) {
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
            response.setRemarkedByName(userNames.get(alert.getRemarkedById()));
        }
        response.setResolvedAt(alert.getResolvedAt());
        response.setResolutionReason(alert.getResolutionReason());

        // Committee Lead's explanation (Overdue alerts only, UC-4.4)
        response.setExplanationStatus(explanationStatus(alert, explanation != null));
        if (explanation != null) {
            response.setExplanation(explanation.getExplanation());
            response.setExplainedAt(explanation.getCreatedAt());
            response.setExplainedByName(userNames.get(explanation.getAuthorId()));
        }

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
