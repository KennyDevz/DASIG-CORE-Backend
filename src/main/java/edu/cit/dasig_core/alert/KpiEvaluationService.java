package edu.cit.dasig_core.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class KpiEvaluationService {

    //private final KpiDefinitionRepository kpiDefinitionRepository;
    private final AlertService alertService;

    public void evaluateSubmission(KpiSubmittedEvent event) {
//        KpiSubmission submission = event.getSubmission();
//
//        KpiDefinition kpiDefinition = kpiDefinitionRepository
//                .findById(event.getKpiId())
//                .orElse(null);
//
//        if (kpiDefinition == null) return;
//
//        if (isBreached(submission.getSubmittedValue(), kpiDefinition.getThresholdValue())) {
//            alertService.createAlert(submission);
//        }
    }

    private boolean isBreached(BigDecimal value, BigDecimal threshold) {
        if (value == null || threshold == null) return false;
        return value.compareTo(threshold) < 0;
    }
}
