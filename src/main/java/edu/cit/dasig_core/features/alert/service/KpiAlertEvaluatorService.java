package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.core.event.KpiEmailEvent;
import edu.cit.dasig_core.core.smtp.EmailService;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPaceCalculator;
import edu.cit.dasig_core.features.notification.service.NotificationService;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates and resolves KPI alerts (UC-4.1).
 * Runs nightly, after each submission, and when the Admin opens the Alerts page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiAlertEvaluatorService {

    public static final String REASON_TARGET_MET = "Target met";
    public static final String REASON_TARGET_MET_LATE = "Target met after deadline";
    public static final String REASON_ESCALATED = "Escalated to Overdue";
    public static final String REASON_RECOVERED = "Progress recovered";

    private static final List<String> OPEN_STATUSES =
            List.of(Alert.STATUS_UNACKNOWLEDGED, Alert.STATUS_ACKNOWLEDGED);

    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final AlertRepository alertRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.business-timezone:Asia/Manila}")
    private String businessTimezone;

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessTimezone));
    }

    @Transactional
    public void evaluateAllKpiAlerts() {
        LocalDate today = today();
        List<KpiDefinition> kpis = kpiDefinitionRepository.findAll().stream()
                .filter(kpi -> kpi.getDeadline() != null && !kpi.isArchived())
                .toList();
        if (kpis.isEmpty()) {
            return;
        }

        // Load totals and open alerts for every KPI in two queries instead of two per KPI
        Map<Long, Double> totals = approvedTotals(kpis.stream().map(KpiDefinition::getId).toList());
        Map<Long, List<Alert>> openAlertsByKpi = alertRepository.findByStatusIn(OPEN_STATUSES).stream()
                .filter(alert -> alert.getKpiDefinitionId() != null)
                .collect(Collectors.groupingBy(Alert::getKpiDefinitionId));

        for (KpiDefinition kpi : kpis) {
            evaluateKpi(kpi, today,
                    totals.getOrDefault(kpi.getId(), 0.0),
                    openAlertsByKpi.getOrDefault(kpi.getId(), List.of()));
        }
    }

    private void evaluateKpi(KpiDefinition kpi, LocalDate today, double actual, List<Alert> openAlerts) {
        double target = kpi.getTargetValue() != null ? kpi.getTargetValue() : 0.0;
        boolean deadlinePassed = today.isAfter(kpi.getDeadline());

        if (target > 0 && actual >= target) {
            // 1. Target met -> close every open alert
            String reason = deadlinePassed ? REASON_TARGET_MET_LATE : REASON_TARGET_MET;
            openAlerts.forEach(alert -> resolve(alert, reason));
        } else if (deadlinePassed) {
            // 2. Deadline passed -> At Risk becomes Overdue
            resolveOpen(openAlerts, Alert.TYPE_AT_RISK, REASON_ESCALATED);
            createIfMissing(kpi, openAlerts, Alert.TYPE_OVERDUE, Alert.SEVERITY_CRITICAL);
        } else if (KpiPaceCalculator.isAtRisk(actual, target, startDate(kpi), kpi.getDeadline(), today)) {
            // 3. Behind pace -> At Risk
            createIfMissing(kpi, openAlerts, Alert.TYPE_AT_RISK, Alert.SEVERITY_WARNING);
        } else {
            // 4. Back on pace -> close At Risk
            resolveOpen(openAlerts, Alert.TYPE_AT_RISK, REASON_RECOVERED);
        }
    }

    /**
     * Sum of approved FINAL submissions per KPI (all organizations under the KPI's committee).
     * KPIs without approved submissions are left out of the map (treat as 0).
     */
    public Map<Long, Double> approvedTotals(Collection<Long> kpiIds) {
        if (kpiIds.isEmpty()) {
            return Map.of();
        }
        return kpiSubmissionRepository.findByKpiDefinitionIdInAndSubmissionType(kpiIds, SubmissionType.FINAL)
                .stream()
                .filter(s -> s.getReviewStatus() == null || s.getReviewStatus() == SubmissionReviewStatus.APPROVED)
                .collect(Collectors.groupingBy(
                        s -> s.getKpiDefinition().getId(),
                        Collectors.summingDouble(s -> s.getSubmittedValue() != null ? s.getSubmittedValue() : 0.0)));
    }

    private LocalDate startDate(KpiDefinition kpi) {
        return kpi.getDateCreated() != null ? kpi.getDateCreated().toLocalDate() : null;
    }

    private void createIfMissing(KpiDefinition kpi, List<Alert> openAlerts, String type, String severity) {
        boolean exists = openAlerts.stream().anyMatch(a -> type.equals(a.getAlertType()));
        if (exists) {
            return;
        }
        Alert alert = new Alert();
        alert.setKpiDefinitionId(kpi.getId());
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setStatus(Alert.STATUS_UNACKNOWLEDGED);
        alertRepository.save(alert);
        // save() populates the generated ID on the managed instance (IDENTITY strategy)
        Long alertId = alert.getId();
        log.info("Created {} alert for KPI: {}", type, kpi.getName());

        notificationService.notifyAlertCreated(kpi, alert);

        // UC-4.5: email the committee's Leads and Members after the alert is saved
        eventPublisher.publishEvent(KpiEmailEvent.alertCreated(kpi.getId(), type));

        // Dispatch email notifications to all active DASIG_ADMIN users
        List<User> activeAdmins = userRepository.findByRoleAndStatus("DASIG_ADMIN", "Active");
        for (User admin : activeAdmins) {
            emailService.sendKpiAlertEmail(admin.getEmail(), admin.getName(), type, severity, kpi.getName(), alertId);
        }
    }

    private void resolveOpen(List<Alert> openAlerts, String type, String reason) {
        openAlerts.stream()
                .filter(a -> type.equals(a.getAlertType()))
                .forEach(a -> resolve(a, reason));
    }

    private void resolve(Alert alert, String reason) {
        alert.setStatus(Alert.STATUS_RESOLVED);
        alert.setResolvedAt(LocalDateTime.now(ZoneId.of(businessTimezone)));
        alert.setResolutionReason(reason);
        alertRepository.save(alert);
        log.info("Resolved {} alert {} ({})", alert.getAlertType(), alert.getId(), reason);
    }
}
