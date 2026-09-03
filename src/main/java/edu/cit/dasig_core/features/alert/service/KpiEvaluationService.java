package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.core.event.KpiSubmittedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KpiEvaluationService {

    private final KpiAlertEvaluatorService kpiAlertEvaluatorService;

    @Transactional
    public void evaluateSubmission(KpiSubmittedEvent event) {
        // Re-evaluate KPI alerts to update overdue/at-risk states with new submission progress
        kpiAlertEvaluatorService.evaluateAllKpiAlerts();
    }
}