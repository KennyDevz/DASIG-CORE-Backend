package edu.cit.dasig_core.features.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ThresholdChecker {
    private final KpiEvaluationService kpiEvaluationService;

    @Async
    @EventListener
    public void detectBreach(KpiSubmittedEvent event){
        kpiEvaluationService.evaluateSubmission(event);
    }

}
