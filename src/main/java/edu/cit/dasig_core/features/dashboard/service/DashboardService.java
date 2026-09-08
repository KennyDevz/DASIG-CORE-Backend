package edu.cit.dasig_core.features.dashboard.service;

import edu.cit.dasig_core.features.dashboard.dto.DashboardKpiItemResponse;
import edu.cit.dasig_core.features.dashboard.dto.DashboardResponse;
import edu.cit.dasig_core.features.dashboard.dto.DashboardCommitteeOption;
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
import edu.cit.dasig_core.features.kpisubmission.util.KpiPeriodProgressCalculator;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPeriodProgressCalculator.KpiPeriodProgress;
import edu.cit.dasig_core.features.kpisubmission.util.PerformanceStatusClassifier;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.committee.repository.CommitteeRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
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
    private final CommitteeRepository committeeRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardForCurrentUser(String reportingPeriod, Long committeeId) {
        User user = resolveCurrentUser();
        List<KpiDefinition> visibleKpis = resolveVisibleKpis(user, committeeId);

        DashboardResponse response = new DashboardResponse();
        response.setRole(user.getRole());
        response.setOrganizationId(user.getOrganizationId());
        response.setOrganizationName(resolveOrganizationName(user));
        response.setCommitteeName(resolveCommitteeName(user, committeeId));
        response.setReportingPeriod(reportingPeriod);
        response.setCommittees(resolveCommitteeOptions(user, committeeId));
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

        Map<String, List<KpiSubmission>> submissionsByPeriod = (user.getOrganizationId() != null
                ? kpiSubmissionRepository.findByKpiDefinitionIdAndOrganizationId(kpiDefinitionId, user.getOrganizationId())
                : kpiSubmissionRepository.findByKpiDefinitionId(kpiDefinitionId))
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
        response.setOrganization(kpiDefinition.getCommittee() != null ? kpiDefinition.getCommittee().getName() : null);
        response.setPeriods(periodItems);
        response.setKpiStatus(kpiDefinition.getStatus());
        response.setArchived(kpiDefinition.isArchived());
        return response;
    }

    private KpiPeriodSubmissionEntryResponse toPeriodSubmissionEntry(KpiSubmission submission) {
        KpiPeriodSubmissionEntryResponse entry = new KpiPeriodSubmissionEntryResponse();
        entry.setId(submission.getId());
        entry.setSubmissionType(submission.getSubmissionType());
        entry.setSubmittedValue(submission.getSubmittedValue());
        entry.setAchievementRate(submission.getAchievementRate());
        entry.setPerformanceStatus(submission.getPerformanceStatus());
        entry.setReviewStatus(submission.getReviewStatus());
        entry.setRejectionReason(submission.getRejectionReason());
        entry.setReviewedByName(submission.getReviewedBy() != null ? submission.getReviewedBy().getName() : null);
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
            return submission.getSubmissionType() == SubmissionType.FINAL;
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

        if ("TBI_MANAGER".equals(user.getRole())) {
            boolean isAssigned = kpiDefinition.getCommittee() != null &&
                    user.getCommittees() != null &&
                    user.getCommittees().stream().anyMatch(c -> c.getId().equals(kpiDefinition.getCommittee().getId()));
            if (!isAssigned) {
                throw new IllegalArgumentException("You do not have access to this KPI.");
            }
            return;
        }

        boolean hasAccess = kpiDefinition.getCommittee() != null &&
                kpiDefinition.getCommittee().getOrganizations().stream()
                        .anyMatch(org -> org.getId().equals(user.getOrganizationId()));

        if (!hasAccess) {
            throw new IllegalArgumentException("You do not have access to this KPI.");
        }
    }

    private List<KpiDefinition> resolveVisibleKpis(User user, Long committeeId) {
        if ("DASIG_ADMIN".equals(user.getRole())) {
            return kpiDefinitionRepository.findAll();
        }

        if (user.getOrganizationId() == null) {
            throw new IllegalArgumentException("Organization is required for this role.");
        }

        if ("TBI_MANAGER".equals(user.getRole())) {
            List<Committee> assignedCommittees = user.getCommittees() != null ? user.getCommittees() : List.of();
            if (assignedCommittees.isEmpty()) {
                return List.of();
            }

            if (committeeId != null) {
                boolean isAssigned = assignedCommittees.stream().anyMatch(c -> c.getId().equals(committeeId));
                if (!isAssigned) {
                    return List.of();
                }
                return kpiDefinitionRepository.findByCommitteeId(committeeId)
                        .stream()
                        .filter(kpi -> !kpi.isArchived())
                        .toList();
            }

            List<Long> assignedCommitteeIds = assignedCommittees.stream().map(Committee::getId).toList();
            return kpiDefinitionRepository.findByCommittee_Organizations_Id(user.getOrganizationId())
                    .stream()
                    .filter(kpi -> !kpi.isArchived())
                    .filter(kpi -> kpi.getCommittee() != null && assignedCommitteeIds.contains(kpi.getCommittee().getId()))
                    .toList();
        }

        if (committeeId != null) {
            return kpiDefinitionRepository.findByCommitteeId(committeeId)
                    .stream()
                    .filter(kpi -> !kpi.isArchived())
                    .toList();
        }

        return kpiDefinitionRepository.findByCommittee_Organizations_Id(user.getOrganizationId())
                .stream()
                .filter(kpi -> !kpi.isArchived())
                .toList();
    }

    private List<DashboardCommitteeOption> resolveCommitteeOptions(User user, Long selectedCommitteeId) {
        if (user.getOrganizationId() == null) {
            return List.of();
        }

        if ("TBI_MANAGER".equals(user.getRole())) {
            List<Committee> assignedCommittees = user.getCommittees() != null ? user.getCommittees() : List.of();
            if (assignedCommittees.isEmpty()) {
                return List.of();
            }

            List<Long> assignedCommitteeIds = assignedCommittees.stream().map(Committee::getId).toList();
            Map<Long, Integer> pendingCounts = kpiSubmissionRepository.countPendingSubmissionsByCommitteeIds(assignedCommitteeIds).stream()
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> ((Number) row[1]).intValue()
                    ));

            return assignedCommittees.stream()
                    .filter(c -> c.getStatus() == null || "Active".equalsIgnoreCase(c.getStatus()))
                    .map(c -> {
                        DashboardCommitteeOption option = new DashboardCommitteeOption();
                        option.setId(c.getId());
                        option.setName(c.getName());
                        option.setCurrent(selectedCommitteeId != null && selectedCommitteeId.equals(c.getId()));
                        int pendingCount = pendingCounts.getOrDefault(c.getId(), 0);
                        option.setPendingSubmissionsCount(pendingCount);
                        option.setHasPendingSubmissions(pendingCount > 0);
                        return option;
                    })
                    .toList();
        }

        Organization org = organizationRepository.findById(user.getOrganizationId()).orElse(null);
        if (org == null) {
            return List.of();
        }

        return org.getCommittees().stream()
                .filter(c -> "Active".equalsIgnoreCase(c.getStatus()))
                .map(c -> {
                    DashboardCommitteeOption option = new DashboardCommitteeOption();
                    option.setId(c.getId());
                    option.setName(c.getName());
                    option.setOrganizationName(org.getName());
                    option.setCurrent(selectedCommitteeId != null && selectedCommitteeId.equals(c.getId()));
                    return option;
                })
                .toList();
    }

    private String resolveCommitteeName(User user, Long committeeId) {
        if (committeeId != null) {
            if ("TBI_MANAGER".equals(user.getRole())) {
                List<Committee> assignedCommittees = user.getCommittees() != null ? user.getCommittees() : List.of();
                boolean isAssigned = assignedCommittees.stream().anyMatch(c -> c.getId().equals(committeeId));
                if (!isAssigned) {
                    return null;
                }
            }
            return committeeRepository.findById(committeeId)
                    .map(Committee::getName)
                    .orElse(null);
        }
        return null;
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

        List<KpiSubmission> relatedSubmissions = user.getOrganizationId() != null
                ? kpiSubmissionRepository.findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(
                        kpiDefinition.getId(),
                        user.getOrganizationId(),
                        submissionType
                )
                : kpiSubmissionRepository.findByKpiDefinitionId(kpiDefinition.getId())
                        .stream()
                        .filter(s -> s.getSubmissionType() == submissionType)
                        .toList();
        KpiPeriodProgress progress = reportingPeriod != null
                ? KpiPeriodProgressCalculator.calculateExisting(kpiDefinition, reportingPeriod, relatedSubmissions)
                : null;

        double submittedValue = progress != null ? progress.cumulativeSubmittedValue() : 0.0;
        double targetValue = progress != null ? progress.expectedTarget() : kpiDefinition.getTargetValue();
        double achievementRate = progress != null ? progress.achievementRate() : 0.0;
        String performanceStatus = progress != null ? progress.performanceStatus() : PerformanceStatusClassifier.RED;

        DashboardKpiItemResponse item = new DashboardKpiItemResponse();
        item.setId(kpiDefinition.getId());
        item.setName(kpiDefinition.getName());
        item.setDescription(kpiDefinition.getDescription());
        item.setTargetValue(targetValue);
        item.setOverallTargetValue(kpiDefinition.getTargetValue());
        item.setPeriodTargetValue(targetValue);
        item.setSubmittedValue(submittedValue);
        item.setUnit(kpiDefinition.getUnit());
        item.setDeadline(kpiDefinition.getDeadline());
        if (kpiDefinition.getCommittee() != null) {
            item.setOrganization(kpiDefinition.getCommittee().getName());
            item.setCommitteeId(kpiDefinition.getCommittee().getId());
            item.setCommitteeName(kpiDefinition.getCommittee().getName());
        }
        item.setAchievementRate(achievementRate);
        item.setStatus(mapStatus(performanceStatus, submittedValue, kpiDefinition.getTargetValue()));
        item.setReportingFrequency(kpiDefinition.getReportingFrequency());
        item.setReportingPeriod(reportingPeriod);
        item.setKpiStatus(kpiDefinition.getStatus());
        item.setArchived(kpiDefinition.isArchived());
        return item;
    }

    private SubmissionType resolveSubmissionTypeForDashboard(String role) {
        if ("DASIG_ADMIN".equals(role) || "TBI_MANAGER".equals(role) || "STAFF".equals(role)) {
            return SubmissionType.FINAL;
        }
        return SubmissionType.INTERNAL;
    }

    /**
     * Maps backend performance status to the dashboard KPI status string.
     *
     * <p>Uses goal completion as the primary signal: if the overall target
     * has been reached, the KPI is COMPLETED regardless of performanceStatus.
     * Otherwise, delegates to the deadline-paced PerformanceStatusClassifier result.</p>
     */
    private String mapStatus(String performanceStatus, double cumulativeSubmittedValue, double overallTargetValue) {
        if (overallTargetValue > 0 && cumulativeSubmittedValue >= overallTargetValue) {
            return "COMPLETED";
        }
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

    private String resolveCommitteeName(User user) {
        if (user.getOrganizationId() == null) {
            return null;
        }

        return organizationRepository.findById(user.getOrganizationId())
                .map(org -> !org.getCommittees().isEmpty() ? org.getCommittees().get(0).getName() : null)
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