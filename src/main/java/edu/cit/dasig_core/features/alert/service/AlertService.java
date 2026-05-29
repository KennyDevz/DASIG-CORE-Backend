package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.features.alert.dto.AlertDetailResponse;
import edu.cit.dasig_core.features.alert.dto.AlertResponse;
import edu.cit.dasig_core.features.alert.model.Alert;
import edu.cit.dasig_core.features.alert.repository.AlertRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPeriodProgressCalculator;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlerts() {
        User user = resolveCurrentUser();
        validateAlertViewerRole(user);

        List<Alert> alerts = "DASIG_ADMIN".equals(user.getRole())
                ? alertRepository.findAllByOrderByDetectedAtDesc()
                : alertRepository.findByOrganizationId(user.getOrganizationId());

        return alerts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AlertDetailResponse getAlertById(Long id) {
        User user = resolveCurrentUser();
        validateAlertViewerRole(user);

        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found with ID: " + id));

        KpiSubmission submission = loadSubmissionForAlert(alert);
        validateAlertAccess(submission, user);

        return toDetailResponse(alert, submission);
    }

    @Transactional
    public AlertDetailResponse acknowledgeAlert(Long id) {
        User user = resolveCurrentUser();
        validateAlertViewerRole(user);

        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found with ID: " + id));

        KpiSubmission submission = loadSubmissionForAlert(alert);
        validateAlertAccess(submission, user);

        if (Alert.STATUS_ACKNOWLEDGED.equals(alert.getStatus())) {
            throw new IllegalArgumentException("Alert is already acknowledged.");
        }

        alert.setStatus(Alert.STATUS_ACKNOWLEDGED);
        Alert savedAlert = alertRepository.save(alert);
        return toDetailResponse(savedAlert, submission);
    }

    public boolean existsForSubmission(Long submissionId) {
        return alertRepository.existsBySubmissionId(submissionId);
    }

    public Alert createBreachAlert(Long submissionId) {
        Alert alert = new Alert();
        alert.setSubmissionId(submissionId);
        alert.setStatus(Alert.STATUS_UNACKNOWLEDGED);
        return alertRepository.save(alert);
    }

    private KpiSubmission loadSubmissionForAlert(Alert alert) {
        return kpiSubmissionRepository.findById(alert.getSubmissionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Submission not found for alert with ID: " + alert.getId()));
    }

    private void validateAlertAccess(KpiSubmission submission, User user) {
        if ("DASIG_ADMIN".equals(user.getRole())) {
            return;
        }

        Long organizationId = submission.getOrganization().getId();
        if (user.getOrganizationId() == null || !user.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("You do not have access to this alert.");
        }
    }

    private void validateAlertViewerRole(User user) {
        if (!"DASIG_ADMIN".equals(user.getRole()) && !"TBI_MANAGER".equals(user.getRole())) {
            throw new IllegalArgumentException("Only DASIG Admins and TBI Managers can view alerts.");
        }

        if ("TBI_MANAGER".equals(user.getRole()) && user.getOrganizationId() == null) {
            throw new IllegalArgumentException("Organization is required to view alerts.");
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

    private AlertResponse toResponse(Alert alert) {
        AlertResponse response = new AlertResponse();
        response.setId(alert.getId());
        response.setSubmissionId(alert.getSubmissionId());
        response.setStatus(alert.getStatus());
        response.setDetectedAt(alert.getDetectedAt());
        return response;
    }

    private AlertDetailResponse toDetailResponse(Alert alert, KpiSubmission submission) {
        AlertDetailResponse response = new AlertDetailResponse();
        response.setId(alert.getId());
        response.setSubmissionId(alert.getSubmissionId());
        response.setStatus(alert.getStatus());
        response.setDetectedAt(alert.getDetectedAt());

        response.setKpiDefinitionId(submission.getKpiDefinition().getId());
        response.setKpiName(submission.getKpiDefinition().getName());
        response.setOrganizationId(submission.getOrganization().getId());
        response.setOrganizationName(submission.getOrganization().getName());

        response.setReportingPeriod(submission.getReportingPeriod());

        // 2. Fetch history for this specific KPI to calculate cumulative state
        List<KpiSubmission> kpiHistory = kpiSubmissionRepository
                .findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(
                        submission.getKpiDefinition().getId(),
                        submission.getOrganization().getId(),
                        SubmissionType.FINAL
                );

        // 3. Run the exact same calculator used in Reports and Dashboards
        KpiPeriodProgressCalculator.KpiPeriodProgress progress =
                KpiPeriodProgressCalculator.calculateExisting(
                        submission.getKpiDefinition(),
                        submission.getReportingPeriod(),
                        kpiHistory
                );

        // 4. Map the new detailed context fields
        response.setPeriodContribution(submission.getSubmittedValue());
        response.setCumulativeValue(progress.cumulativeSubmittedValue());
        response.setScaledPeriodTarget(progress.expectedTarget());

        response.setSubmittedValue(submission.getSubmittedValue());
        response.setSubmissionDate(submission.getSubmissionDate());
        response.setAchievementRate(submission.getAchievementRate());
        response.setPerformanceStatus(submission.getPerformanceStatus());
        response.setSubmissionType(submission.getSubmissionType());
        return response;
    }
}
