package edu.cit.dasig_core.features.dashboard.service;

import edu.cit.dasig_core.features.dashboard.dto.DashboardKpiItemResponse;
import edu.cit.dasig_core.features.dashboard.dto.DashboardResponse;
import edu.cit.dasig_core.features.dashboard.dto.KpiPeriodHistoryItemResponse;
import edu.cit.dasig_core.features.dashboard.dto.KpiPeriodHistoryResponse;
import edu.cit.dasig_core.features.dashboard.dto.KpiPeriodSubmissionEntryResponse;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import edu.cit.dasig_core.features.kpi.util.ReportingPeriodResolver;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiSubmissionRepository kpiSubmissionRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardForCurrentUser(String reportingPeriod) {
        User user = resolveCurrentUser();
        List<KpiDefinition> visibleKpis = resolveVisibleKpis(user);

        DashboardResponse response = new DashboardResponse();
        response.setRole(user.getRole());
        response.setOrganizationId(user.getOrganizationId());
        response.setOrganizationName(resolveOrganizationName(user));
        response.setReportingPeriod(reportingPeriod);
        response.setKpis(visibleKpis.stream()
                .map(kpi -> toDashboardKpiItem(kpi, user, reportingPeriod))
                .toList());

        return response;
    }

    @Transactional(readOnly = true)
    public KpiPeriodHistoryResponse getKpiPeriodHistory(Long kpiDefinitionId) {
        User user = resolveCurrentUser();
        KpiDefinition kpiDefinition = kpiDefinitionRepository.findById(kpiDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found with ID: " + kpiDefinitionId));
        validateKpiAccess(user, kpiDefinition);

        LocalDate assignmentStart = kpiDefinition.getDateCreated() != null
                ? kpiDefinition.getDateCreated().toLocalDate()
                : LocalDate.now();
        String currentPeriod = ReportingPeriodResolver.resolveCurrentPeriod(
                kpiDefinition.getReportingFrequency(),
                kpiDefinition.getDeadline(),
                assignmentStart,
                LocalDate.now()
        );

        List<String> periodOptions = ReportingPeriodResolver.generatePeriodOptions(
                kpiDefinition.getReportingFrequency(),
                kpiDefinition.getDeadline(),
                assignmentStart
        );
        List<String> orderedPeriods = new ArrayList<>(periodOptions);
        Collections.reverse(orderedPeriods);

        Map<String, List<KpiSubmission>> submissionsByPeriod = kpiSubmissionRepository
                .findByKpiDefinitionId(kpiDefinitionId)
                .stream()
                .filter(submission -> matchesHistoryVisibility(user, submission))
                .collect(Collectors.groupingBy(KpiSubmission::getReportingPeriod));

        List<KpiPeriodHistoryItemResponse> periodItems = orderedPeriods.stream()
                .map(period -> {
                    KpiPeriodHistoryItemResponse item = new KpiPeriodHistoryItemResponse();
                    item.setReportingPeriod(period);
                    item.setCurrent(period.equals(currentPeriod));
                    item.setSubmissions(submissionsByPeriod.getOrDefault(period, List.of())
                            .stream()
                            .sorted(Comparator.comparing(KpiSubmission::getSubmissionType))
                            .map(this::toPeriodSubmissionEntry)
                            .toList());
                    return item;
                })
                .toList();

        KpiPeriodHistoryResponse response = new KpiPeriodHistoryResponse();
        response.setKpiDefinitionId(kpiDefinition.getId());
        response.setName(kpiDefinition.getName());
        response.setDescription(kpiDefinition.getDescription());
        response.setTargetValue(kpiDefinition.getTargetValue());
        response.setUnit(kpiDefinition.getUnit());
        response.setDeadline(kpiDefinition.getDeadline());
        response.setReportingFrequency(kpiDefinition.getReportingFrequency());
        response.setCurrentPeriod(currentPeriod);
        response.setOrganization(kpiDefinition.getOrganization().getName());
        response.setPeriods(periodItems);
        return response;
    }

    private KpiPeriodSubmissionEntryResponse toPeriodSubmissionEntry(KpiSubmission submission) {
        KpiPeriodSubmissionEntryResponse entry = new KpiPeriodSubmissionEntryResponse();
        entry.setId(submission.getId());
        entry.setSubmissionType(submission.getSubmissionType());
        entry.setSubmittedValue(submission.getSubmittedValue());
        entry.setAchievementRate(submission.getAchievementRate());
        entry.setPerformanceStatus(submission.getPerformanceStatus());
        entry.setSubmittedByName(submission.getSubmittedBy().getName());
        entry.setSubmittedByRole(submission.getSubmittedBy().getRole());
        entry.setSubmissionDate(submission.getSubmissionDate());
        return entry;
    }

    private boolean matchesHistoryVisibility(User user, KpiSubmission submission) {
        if ("DASIG_ADMIN".equals(user.getRole())) {
            return submission.getSubmissionType() == SubmissionType.FINAL;
        }
        if ("STAFF".equals(user.getRole())) {
            return submission.getSubmissionType() == SubmissionType.INTERNAL;
        }
        return true;
    }

    private void validateKpiAccess(User user, KpiDefinition kpiDefinition) {
        if ("DASIG_ADMIN".equals(user.getRole())) {
            return;
        }

        if (user.getOrganizationId() == null) {
            throw new IllegalArgumentException("Organization is required for this role.");
        }

        if (!user.getOrganizationId().equals(kpiDefinition.getOrganization().getId())) {
            throw new IllegalArgumentException("You do not have access to this KPI.");
        }
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

    private DashboardKpiItemResponse toDashboardKpiItem(
            KpiDefinition kpiDefinition,
            User user,
            String requestedReportingPeriod
    ) {
        SubmissionType submissionType = resolveSubmissionTypeForDashboard(user.getRole());
        LocalDate assignmentStart = kpiDefinition.getDateCreated() != null
                ? kpiDefinition.getDateCreated().toLocalDate()
                : LocalDate.now();
        String reportingPeriod = requestedReportingPeriod != null && !requestedReportingPeriod.isBlank()
                ? requestedReportingPeriod
                : ReportingPeriodResolver.resolveCurrentPeriod(
                        kpiDefinition.getReportingFrequency(),
                        kpiDefinition.getDeadline(),
                        assignmentStart,
                        LocalDate.now()
                );

        KpiSubmission latestSubmission = reportingPeriod != null
                ? kpiSubmissionRepository
                        .findByKpiDefinitionIdAndReportingPeriodAndSubmissionType(
                                kpiDefinition.getId(),
                                reportingPeriod,
                                submissionType
                        )
                        .orElse(null)
                : null;

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
        item.setReportingFrequency(kpiDefinition.getReportingFrequency());
        item.setReportingPeriod(reportingPeriod);
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
