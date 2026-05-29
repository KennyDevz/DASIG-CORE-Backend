package edu.cit.dasig_core.features.dashboard.service;

import edu.cit.dasig_core.features.dashboard.dto.DashboardKpiItemResponse;
import edu.cit.dasig_core.features.dashboard.dto.DashboardResponse;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.kpisubmission.util.KpiAchievementCalculator;
import edu.cit.dasig_core.features.kpisubmission.util.PerformanceStatusClassifier;
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
public class DashboardService {

    private final UserRepository userRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardForCurrentUser() {
        User user = resolveCurrentUser();
        List<KpiDefinition> visibleKpis = resolveVisibleKpis(user);

        DashboardResponse response = new DashboardResponse();
        response.setRole(user.getRole());
        response.setOrganizationId(user.getOrganizationId());
        response.setOrganizationName(resolveOrganizationName(user));
        response.setKpis(visibleKpis.stream().map(kpi -> toDashboardKpiItem(kpi, user)).toList());

        return response;
    }

    private List<KpiDefinition> resolveVisibleKpis(User user) {
        if ("DASIG_ADMIN".equals(user.getRole())) {
            return kpiDefinitionRepository.findAll();
        }

        if (user.getOrganizationId() == null) {
            throw new IllegalArgumentException("Organization is required for this role.");
        }

        return kpiDefinitionRepository.findByOrganizationId(user.getOrganizationId());
    }

    private DashboardKpiItemResponse toDashboardKpiItem(KpiDefinition kpiDefinition, User user) {
        SubmissionType submissionType = resolveSubmissionTypeForDashboard(user.getRole());
        KpiSubmission latestSubmission = kpiSubmissionRepository
                .findFirstByKpiDefinitionIdAndSubmissionTypeOrderByDateCreatedDesc(
                        kpiDefinition.getId(),
                        submissionType
                )
                .orElse(null);

        double submittedValue = latestSubmission != null ? latestSubmission.getSubmittedValue() : 0.0;
        double achievementRate = latestSubmission != null
                ? latestSubmission.getAchievementRate()
                : KpiAchievementCalculator.calculate(submittedValue, kpiDefinition.getTargetValue());

        String performanceStatus = latestSubmission != null
                ? latestSubmission.getPerformanceStatus()
                : PerformanceStatusClassifier.classify(achievementRate, kpiDefinition.getThreshold());

        DashboardKpiItemResponse item = new DashboardKpiItemResponse();
        item.setId(kpiDefinition.getId());
        item.setName(kpiDefinition.getName());
        item.setDescription(kpiDefinition.getDescription());
        item.setTargetValue(kpiDefinition.getTargetValue());
        item.setSubmittedValue(submittedValue);
        item.setUnit(kpiDefinition.getUnit());
        item.setDeadline(kpiDefinition.getDeadline());
        item.setOrganization(kpiDefinition.getOrganization().getName());
        item.setAchievementRate(achievementRate);
        item.setStatus(mapStatus(performanceStatus));
        return item;
    }

    private SubmissionType resolveSubmissionTypeForDashboard(String role) {
        if ("DASIG_ADMIN".equals(role)) {
            return SubmissionType.FINAL;
        }
        return SubmissionType.INTERNAL;
    }

    private String mapStatus(String performanceStatus) {
        if (PerformanceStatusClassifier.GREEN.equals(performanceStatus)) {
            return "ON_TRACK";
        }
        if (PerformanceStatusClassifier.YELLOW.equals(performanceStatus)) {
            return "AT_RISK";
        }
        return "DELAYED";
    }

    private String resolveOrganizationName(User user) {
        if (user.getOrganizationId() == null) {
            return null;
        }

        return organizationRepository.findById(user.getOrganizationId())
                .map(organization -> organization.getName())
                .orElse(null);
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
}
