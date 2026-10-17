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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Overdue explanations (UC-4.4): the Committee Lead writes one explanation per Overdue alert
 * for KPIs assigned to their committees. The Admin sees it on the alert.
 */
@Service
@RequiredArgsConstructor
public class OverdueExplanationService {

    public static final String STATUS_AWAITING = "Awaiting explanation";
    public static final String STATUS_EXPLAINED = "Explained";

    private final AlertRepository alertRepository;
    private final OverdueExplanationRepository overdueExplanationRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final UserRepository userRepository;

    /** Overdue alerts for the KPIs of the current Committee Lead's committees, newest first. */
    @Transactional(readOnly = true)
    public List<CommitteeOverdueAlertResponse> getOverdueAlertsForCommitteeLead() {
        User lead = resolveCommitteeLead();

        List<CommitteeOverdueAlertResponse> result = new ArrayList<>();
        for (Committee committee : lead.getCommittees()) {
            for (KpiDefinition kpi : kpiDefinitionRepository.findByCommitteeId(committee.getId())) {
                for (Alert alert : alertRepository.findByKpiDefinitionIdAndAlertType(kpi.getId(), Alert.TYPE_OVERDUE)) {
                    result.add(toResponse(alert, kpi));
                }
            }
        }
        result.sort(Comparator.comparing(CommitteeOverdueAlertResponse::getDetectedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /** The Committee Lead submits the one explanation for an Overdue alert. */
    @Transactional
    public CommitteeOverdueAlertResponse submitExplanation(Long alertId, String text) {
        User lead = resolveCommitteeLead();

        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found with ID: " + alertId));
        if (!Alert.TYPE_OVERDUE.equals(alert.getAlertType())) {
            throw new IllegalArgumentException("Explanations can only be submitted for Overdue alerts.");
        }

        KpiDefinition kpi = kpiDefinitionRepository.findById(alert.getKpiDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException("KPI not found for alert with ID: " + alertId));
        if (!leadsCommittee(lead, kpi)) {
            throw new IllegalArgumentException("You can only explain Overdue alerts for your own committees.");
        }
        if (overdueExplanationRepository.existsByAlertId(alertId)) {
            throw new IllegalArgumentException("An explanation has already been submitted for this alert.");
        }

        OverdueExplanation explanation = new OverdueExplanation();
        explanation.setAlertId(alertId);
        explanation.setKpiDefinitionId(kpi.getId());
        explanation.setExplanation(text.trim());
        explanation.setAuthorId(lead.getId());
        explanation.setAuthorRole(lead.getRole());
        overdueExplanationRepository.save(explanation);

        return toResponse(alert, kpi);
    }

    private CommitteeOverdueAlertResponse toResponse(Alert alert, KpiDefinition kpi) {
        CommitteeOverdueAlertResponse response = new CommitteeOverdueAlertResponse();
        response.setAlertId(alert.getId());
        response.setKpiDefinitionId(kpi.getId());
        response.setKpiName(kpi.getName());
        response.setCommitteeName(kpi.getCommittee() != null ? kpi.getCommittee().getName() : null);
        response.setDeadline(kpi.getDeadline());
        response.setAlertStatus(alert.getStatus());
        response.setDetectedAt(alert.getDetectedAt());

        overdueExplanationRepository.findByAlertId(alert.getId()).ifPresentOrElse(
                explanation -> {
                    response.setExplanationStatus(STATUS_EXPLAINED);
                    response.setExplanation(explanation.getExplanation());
                    response.setExplainedAt(explanation.getCreatedAt());
                },
                () -> response.setExplanationStatus(STATUS_AWAITING));
        return response;
    }

    private boolean leadsCommittee(User lead, KpiDefinition kpi) {
        return kpi.getCommittee() != null
                && lead.getCommittees().stream().anyMatch(c -> c.getId().equals(kpi.getCommittee().getId()));
    }

    private User resolveCommitteeLead() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
        if (!"Active".equals(user.getStatus())) {
            throw new IllegalArgumentException("Account is not active.");
        }
        if (!"TBI_MANAGER".equals(user.getRole())) {
            throw new IllegalArgumentException("Only Committee Leads can explain Overdue alerts.");
        }
        return user;
    }
}
