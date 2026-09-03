package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KpiAlertEvaluatorService {

    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final AlertRepository alertRepository;

    @Value("${app.business-timezone:Asia/Manila}")
    private String businessTimezone;

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessTimezone));
    }

    @Transactional
    public void evaluateAllKpiAlerts() {
        List<KpiDefinition> kpiDefinitions = kpiDefinitionRepository.findAll();
        LocalDate now = today();

        for (KpiDefinition kpi : kpiDefinitions) {
            if (kpi.getDeadline() == null) {
                continue;
            }

            // Sum all approved official final submissions for this KPI
            List<KpiSubmission> finalSubmissions = kpiSubmissionRepository
                    .findByKpiDefinitionIdAndSubmissionType(kpi.getId(), SubmissionType.FINAL);

            double cumulativeSubmitted = finalSubmissions.stream()
                    .filter(s -> s.getReviewStatus() == null || s.getReviewStatus() == SubmissionReviewStatus.APPROVED)
                    .mapToDouble(s -> s.getSubmittedValue() != null ? s.getSubmittedValue() : 0.0)
                    .sum();

            double target = kpi.getTargetValue() != null ? kpi.getTargetValue() : 0.0;
            boolean targetAchieved = target > 0 && cumulativeSubmitted >= target;

            // If target is met, no overdue or at-risk alerts needed
            if (targetAchieved) {
                continue;
            }

            long daysUntilDeadline = ChronoUnit.DAYS.between(now, kpi.getDeadline());

            // 1. OVERDUE (Critical): Deadline passed without reaching target
            if (daysUntilDeadline < 0) {
                ensureSingleAlert(kpi, Alert.TYPE_OVERDUE, Alert.SEVERITY_CRITICAL);
            }
            // 2. AT RISK (Warning): Deadline <= 60 days AND progress < 50%
            else if (daysUntilDeadline <= 60) {
                double progressRatio = target > 0 ? cumulativeSubmitted / target : 0.0;
                if (progressRatio < 0.50) {
                    ensureSingleAlert(kpi, Alert.TYPE_AT_RISK, Alert.SEVERITY_WARNING);
                }
            }
        }
    }

    private void ensureSingleAlert(KpiDefinition kpi, String alertType, String severity) {
        List<Alert> existingAlerts = alertRepository.findByKpiDefinitionIdAndAlertType(kpi.getId(), alertType);

        if (!existingAlerts.isEmpty()) {
            // If duplicate alerts exist in the database for this KPI and alertType, clean them up!
            if (existingAlerts.size() > 1) {
                boolean hasAcknowledged = existingAlerts.stream()
                        .anyMatch(a -> Alert.STATUS_ACKNOWLEDGED.equals(a.getStatus()));

                if (hasAcknowledged) {
                    List<Alert> toDelete = existingAlerts.stream()
                            .filter(a -> Alert.STATUS_UNACKNOWLEDGED.equals(a.getStatus()))
                            .toList();
                    alertRepository.deleteAll(toDelete);
                } else {
                    List<Alert> toDelete = existingAlerts.subList(1, existingAlerts.size());
                    alertRepository.deleteAll(toDelete);
                }
            }
            // Alert already exists (acknowledged or active); do NOT create duplicate
            return;
        }

        // No alert exists yet: create one
        createAlertForKpi(kpi, alertType, severity, null);
    }

    private void createAlertForKpi(KpiDefinition kpi, String alertType, String severity, Long submissionId) {
        Alert alert = new Alert();
        alert.setKpiDefinitionId(kpi.getId());
        alert.setSubmissionId(submissionId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setStatus(Alert.STATUS_UNACKNOWLEDGED);

        alertRepository.save(alert);
        log.info("Created {} alert (severity: {}) for KPI: {}", alertType, severity, kpi.getName());
    }
}