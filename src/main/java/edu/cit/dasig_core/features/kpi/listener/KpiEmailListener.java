package edu.cit.dasig_core.features.kpi.listener;

import edu.cit.dasig_core.core.event.KpiEmailEvent;
import edu.cit.dasig_core.core.event.KpiEmailType;
import edu.cit.dasig_core.core.smtp.EmailService;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.repository.KpiSubmissionRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the KPI and submission emails of UC-4.5:
 * KPI assigned, deadline updated and new At Risk / Overdue alerts (committee Leads and Members), submission sent (committee Leads),
 * submission reviewed (the submitting Member) and final value approved (Admins).
 * Runs in the background after the change is saved, so a slow or failed email never blocks or undoes the save.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiEmailListener {

    private static final String ACTIVE = "Active";

    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final KpiSubmissionRepository kpiSubmissionRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onKpiEmailEvent(KpiEmailEvent event) {
        if (event.submissionId() != null) {
            sendSubmissionEmails(event);
        } else {
            sendKpiEmails(event);
        }
    }

    // ---- KPI assigned / deadline updated ----

    private void sendKpiEmails(KpiEmailEvent event) {
        KpiDefinition kpi = kpiDefinitionRepository.findById(event.kpiDefinitionId()).orElse(null);
        if (kpi == null || kpi.getCommittee() == null) {
            log.warn("Skipping {} email: KPI {} or its committee was not found", event.type(), event.kpiDefinitionId());
            return;
        }

        Committee committee = kpi.getCommittee();
        String target = formatTarget(kpi.getTargetValue(), kpi.getUnit());
        for (User user : findRecipients(committee)) {
            switch (event.type()) {
                case KPI_ASSIGNED -> emailService.sendKpiAssignedEmail(user.getEmail(), user.getName(), user.getRole(),
                        kpi.getId(), kpi.getName(), committee.getName(), target, kpi.getDeadline());
                case DEADLINE_UPDATED -> emailService.sendKpiDeadlineUpdatedEmail(user.getEmail(), user.getName(), user.getRole(),
                        kpi.getId(), kpi.getName(), committee.getName(),
                        event.oldDeadline(), event.newDeadline(), event.adminRemark());
                case KPI_AT_RISK, KPI_OVERDUE -> emailService.sendKpiAlertCommitteeEmail(user.getEmail(), user.getName(),
                        user.getRole(), kpi.getName(), committee.getName(),
                        event.type() == KpiEmailType.KPI_OVERDUE, target, kpi.getDeadline());
                default -> log.warn("Unexpected KPI email type {}", event.type());
            }
        }
    }

    // ---- Submission sent / reviewed / final value approved ----

    private void sendSubmissionEmails(KpiEmailEvent event) {
        KpiSubmission submission = kpiSubmissionRepository.findById(event.submissionId()).orElse(null);
        if (submission == null || submission.getKpiDefinition() == null) {
            log.warn("Skipping {} email: submission {} was not found", event.type(), event.submissionId());
            return;
        }

        KpiDefinition kpi = submission.getKpiDefinition();
        Committee committee = kpi.getCommittee();
        String committeeName = committee != null ? committee.getName() : "";
        String organizationName = submission.getOrganization() != null ? submission.getOrganization().getName() : "";
        String value = formatTarget(submission.getSubmittedValue(), kpi.getUnit());

        switch (event.type()) {
            case SUBMISSION_SENT -> {
                if (committee == null) {
                    return;
                }
                String submitter = nameOf(submission.getSubmittedBy());
                for (User lead : uniqueByEmail(userRepository.findByRoleAndStatusAndCommittees_Id("TBI_MANAGER", ACTIVE, committee.getId()))) {
                    emailService.sendSubmissionForReviewEmail(lead.getEmail(), lead.getName(), kpi.getName(),
                            organizationName, submitter, value, submission.getReportingPeriod(),
                            submission.getSubmissionDate(), submission.getReferenceCode());
                }
            }
            case SUBMISSION_REVIEWED -> {
                User member = submission.getSubmittedBy();
                if (member == null || !ACTIVE.equals(member.getStatus()) || isBlank(member.getEmail())) {
                    return;
                }
                emailService.sendSubmissionReviewedEmail(member.getEmail(), member.getName(), kpi.getName(), value,
                        submission.getReferenceCode(), submission.getReviewStatus() == SubmissionReviewStatus.APPROVED,
                        nameOf(submission.getReviewedBy()), submission.getRejectionReason());
            }
            case FINAL_VALUE_APPROVED -> {
                for (User admin : uniqueByEmail(userRepository.findByRoleAndStatus("DASIG_ADMIN", ACTIVE))) {
                    emailService.sendFinalValueApprovedEmail(admin.getEmail(), admin.getName(), kpi.getId(),
                            kpi.getName(), committeeName, organizationName, value,
                            submission.getReportingPeriod(), nameOf(submission.getReviewedBy()));
                }
            }
            default -> log.warn("Unexpected submission email type {}", event.type());
        }
    }

    // ---- Recipients ----

    /** Active Committee Leads of the committee and active Members of its organizations, one per email address. */
    List<User> findRecipients(Committee committee) {
        List<User> users = new ArrayList<>(userRepository.findByRoleAndStatusAndCommittees_Id("TBI_MANAGER", ACTIVE, committee.getId()));

        List<Long> organizationIds = committee.getOrganizations() == null ? List.of()
                : committee.getOrganizations().stream().map(Organization::getId).toList();
        if (!organizationIds.isEmpty()) {
            users.addAll(userRepository.findByRoleAndStatusAndOrganizationIdIn("STAFF", ACTIVE, organizationIds));
        }
        return uniqueByEmail(users);
    }

    /** Drops users without an email and keeps one user per email address (case-insensitive). */
    private List<User> uniqueByEmail(List<User> users) {
        Map<String, User> byEmail = new LinkedHashMap<>();
        for (User user : users) {
            if (!isBlank(user.getEmail())) {
                byEmail.putIfAbsent(user.getEmail().trim().toLowerCase(), user);
            }
        }
        return new ArrayList<>(byEmail.values());
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static String nameOf(User user) {
        return user != null && user.getName() != null ? user.getName() : "";
    }

    /** "100 startups" for whole numbers, "12.5 %" otherwise. */
    static String formatTarget(Double targetValue, String unit) {
        if (targetValue == null) {
            return "";
        }
        String value = targetValue % 1 == 0
                ? String.valueOf(targetValue.longValue())
                : String.valueOf(targetValue);
        return unit == null || unit.isBlank() ? value : value + " " + unit;
    }
}
