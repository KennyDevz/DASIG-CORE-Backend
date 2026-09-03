package edu.cit.dasig_core.features.alert.scheduler;

import edu.cit.dasig_core.features.alert.service.KpiAlertEvaluatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KpiAlertScheduler {

    private final KpiAlertEvaluatorService kpiAlertEvaluatorService;

    @Scheduled(cron = "0 0 0 * * ?", zone = "${app.business-timezone:Asia/Manila}")
    public void evaluateNightlyAlerts() {
        log.info("Running nightly KPI alert evaluation at 12:00 AM...");
        kpiAlertEvaluatorService.evaluateAllKpiAlerts();
    }
}