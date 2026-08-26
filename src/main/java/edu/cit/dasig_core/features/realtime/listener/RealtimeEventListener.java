package edu.cit.dasig_core.features.realtime.listener;

import edu.cit.dasig_core.core.event.KpiDefinitionChangedEvent;
import edu.cit.dasig_core.core.event.KpiSubmittedEvent;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import edu.cit.dasig_core.features.realtime.service.RealtimeEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.HashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeEventListener {

    private final RealtimeEmitterService realtimeEmitterService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleKpiSubmitted(KpiSubmittedEvent event) {
        log.info("Broadcasting live update for KPI Submission ID: {} ({})",
                event.getSubmissionId(), event.getSubmissionType());

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "KPI_SUBMITTED");
        payload.put("submissionId", event.getSubmissionId());
        payload.put("submittedValue", event.getSubmittedValue());
        payload.put("timestamp", System.currentTimeMillis());

        if (event.getSubmissionType() == SubmissionType.INTERNAL) {
            // Staff -> TBI Manager: only the reviewing TBI Manager for this organization needs to know.
            realtimeEmitterService.broadcastToOrganizationRole(
                    event.getOrganizationId(), "TBI_MANAGER", "KPI_UPDATE", payload);
        } else {
            // TBI Manager -> Admin: the official submission is ready for admin review.
            realtimeEmitterService.broadcastToRole("DASIG_ADMIN", "KPI_UPDATE", payload);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleKpiDefinitionChanged(KpiDefinitionChangedEvent event) {
        log.info("Broadcasting live update for KPI Definition ID: {} ({})",
                event.getKpiDefinitionId(), event.getChangeType());

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "KPI_DEFINITION_" + event.getChangeType());
        payload.put("kpiDefinitionId", event.getKpiDefinitionId());
        payload.put("organizationId", event.getOrganizationId());
        payload.put("timestamp", System.currentTimeMillis());

        realtimeEmitterService.broadcast("KPI_DEFINITION_UPDATE", payload);
    }
}