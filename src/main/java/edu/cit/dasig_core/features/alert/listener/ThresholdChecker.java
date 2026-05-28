package edu.cit.dasig_core.features.alert.listener;

import edu.cit.dasig_core.core.event.KpiSubmittedEvent;
import edu.cit.dasig_core.features.alert.service.KpiEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class ThresholdChecker {
    private final KpiEvaluationService kpiEvaluationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void detectBreach(KpiSubmittedEvent event) {
        kpiEvaluationService.evaluateSubmission(event);
    }

}
