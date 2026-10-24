package edu.cit.dasig_core.features.kpi.listener;

import edu.cit.dasig_core.core.event.KpiEmailEvent;
import edu.cit.dasig_core.core.event.KpiEmailType;
import edu.cit.dasig_core.core.smtp.EmailService;
import edu.cit.dasig_core.features.committee.model.Committee;
import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.repository.KpiDefinitionRepository;
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
 * Emails the committee's active Leads and Members when a KPI is assigned or its deadline changes (UC-4.5).
 * Runs in the background after the KPI change is saved, so a slow or failed email never blocks or undoes the save.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiEmailListener {

    private static final String ACTIVE = "Active";

    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onKpiEmailEvent(KpiEmailEvent event) {
        KpiDefinition kpi = kpiDefinitionRepository.findById(event.kpiDefinitionId()).orElse(null);
        if (kpi == null || kpi.getCommittee() == null) {
            log.warn("Skipping {} email: KPI {} or its committee was not found", event.type(), event.kpiDefinitionId());
            return;
        }

        Committee committee = kpi.getCommittee();
        for (User user : findRecipients(committee)) {
            if (event.type() == KpiEmailType.KPI_ASSIGNED) {
                emailService.sendKpiAssignedEmail(user.getEmail(), user.getName(), user.getRole(),
                        kpi.getId(), kpi.getName(), committee.getName(),
                        formatTarget(kpi.getTargetValue(), kpi.getUnit()), kpi.getDeadline());
            } else {
                emailService.sendKpiDeadlineUpdatedEmail(user.getEmail(), user.getName(), user.getRole(),
                        kpi.getId(), kpi.getName(), committee.getName(),
                        event.oldDeadline(), event.newDeadline(), event.adminRemark());
            }
        }
    }

    /** Active Committee Leads of the committee and active Members of its organizations, one per email address. */
    List<User> findRecipients(Committee committee) {
        Map<String, User> byEmail = new LinkedHashMap<>();
        userRepository.findByRoleAndStatusAndCommittees_Id("TBI_MANAGER", ACTIVE, committee.getId())
                .forEach(user -> addRecipient(byEmail, user));

        List<Long> organizationIds = committee.getOrganizations() == null ? List.of()
                : committee.getOrganizations().stream().map(Organization::getId).toList();
        if (!organizationIds.isEmpty()) {
            userRepository.findByRoleAndStatusAndOrganizationIdIn("STAFF", ACTIVE, organizationIds)
                    .forEach(user -> addRecipient(byEmail, user));
        }
        return new ArrayList<>(byEmail.values());
    }

    private void addRecipient(Map<String, User> byEmail, User user) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            byEmail.putIfAbsent(user.getEmail().trim().toLowerCase(), user);
        }
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
