package edu.cit.dasig_core.features.alert.service;

import edu.cit.dasig_core.core.event.KpiSubmittedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KpiEvaluationServiceTest {

    @Mock
    private KpiAlertEvaluatorService kpiAlertEvaluatorService;

    private KpiEvaluationService kpiEvaluationService;

    @BeforeEach
    void setUp() {
        kpiEvaluationService = new KpiEvaluationService(kpiAlertEvaluatorService);
    }

    @Test
    void evaluateSubmission_triggersFullAlertReEvaluation() {
        KpiSubmittedEvent event = new KpiSubmittedEvent(1L, BigDecimal.TEN);

        kpiEvaluationService.evaluateSubmission(event);

        verify(kpiAlertEvaluatorService).evaluateAllKpiAlerts();
    }
}
