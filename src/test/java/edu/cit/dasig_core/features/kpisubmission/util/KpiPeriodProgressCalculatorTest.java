package edu.cit.dasig_core.features.kpisubmission.util;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.util.KpiPeriodProgressCalculator.KpiPeriodProgress;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KpiPeriodProgressCalculatorTest {

    @Test
    void calculatesQuarterlyCumulativeProgressFromPeriodContributions() {
        KpiDefinition kpi = kpiDefinition(ReportingFrequency.QUARTERLY, 100.0, 80.0);

        KpiPeriodProgress q1 = KpiPeriodProgressCalculator.calculate(kpi, "Q1 2026", List.of(), 35.0);
        KpiPeriodProgress q2 = KpiPeriodProgressCalculator.calculate(kpi, "Q2 2026", List.of(
                submission("Q1 2026", 35.0)
        ), 4.0);
        KpiPeriodProgress q3 = KpiPeriodProgressCalculator.calculate(kpi, "Q3 2026", List.of(
                submission("Q1 2026", 35.0),
                submission("Q2 2026", 4.0)
        ), 15.0);
        KpiPeriodProgress q4 = KpiPeriodProgressCalculator.calculate(kpi, "Q4 2026", List.of(
                submission("Q1 2026", 35.0),
                submission("Q2 2026", 4.0),
                submission("Q3 2026", 15.0)
        ), 50.0);

        // expectedThreshold now equals expectedTarget (threshold % is no longer applied per-period).
        // performanceStatus is now deadline-and-goal-driven: GREEN unless deadline passed or ≤60 days with <50% progress.
        // Deadline 2026-12-31 is ~117 days away from test date, so all mid-year periods show GREEN.
        assertProgress(q1, 25.0, 25.0, 35.0, 140.0, PerformanceStatusClassifier.GREEN);
        assertProgress(q2, 50.0, 50.0, 39.0, 78.0, PerformanceStatusClassifier.GREEN);
        assertProgress(q3, 75.0, 75.0, 54.0, 72.0, PerformanceStatusClassifier.GREEN);
        assertProgress(q4, 100.0, 100.0, 104.0, 104.0, PerformanceStatusClassifier.GREEN);
    }

    @Test
    void calculatesMonthlyCumulativeProgress() {
        KpiDefinition kpi = kpiDefinition(ReportingFrequency.MONTHLY, 120.0, 80.0);

        KpiPeriodProgress progress = KpiPeriodProgressCalculator.calculate(kpi, "Mar 2026", List.of(
                submission("Jan 2026", 8.0),
                submission("Feb 2026", 8.0)
        ), 8.0);

        // expectedThreshold = expectedTarget (30.0). Deadline far away → GREEN, not YELLOW.
        assertProgress(progress, 30.0, 30.0, 24.0, 80.0, PerformanceStatusClassifier.GREEN);
    }

    @Test
    void treatsThresholdAsPercentageOfExpectedPeriodTarget() {
        KpiDefinition kpi = kpiDefinition(ReportingFrequency.QUARTERLY, 600.0, 80.0);

        KpiPeriodProgress progress = KpiPeriodProgressCalculator.calculate(kpi, "Q1 2026", List.of(), 100.0);

        // expectedThreshold = expectedTarget (150.0). Deadline far away → GREEN, not RED.
        assertProgress(progress, 150.0, 150.0, 100.0, 66.67, PerformanceStatusClassifier.GREEN);
    }

    @Test
    void sumsMultipleSubmissionsInTheCurrentPeriod() {
        KpiDefinition kpi = kpiDefinition(ReportingFrequency.MONTHLY, 120.0, 80.0);

        KpiPeriodProgress progress = KpiPeriodProgressCalculator.calculateExisting(kpi, "Mar 2026", List.of(
                submission("Jan 2026", 8.0),
                submission("Feb 2026", 8.0),
                submission("Mar 2026", 5.0),
                submission("Mar 2026", 3.0)
        ));

        // expectedThreshold = expectedTarget (30.0). Deadline far away → GREEN, not YELLOW.
        assertProgress(progress, 30.0, 30.0, 24.0, 80.0, PerformanceStatusClassifier.GREEN);
    }

    @Test
    void includesExistingSamePeriodValuesWhenCreatingNewSubmission() {
        KpiDefinition kpi = kpiDefinition(ReportingFrequency.MONTHLY, 120.0, 80.0);

        KpiPeriodProgress progress = KpiPeriodProgressCalculator.calculateWithNewSubmission(kpi, "Mar 2026", List.of(
                submission("Jan 2026", 8.0),
                submission("Feb 2026", 8.0),
                submission("Mar 2026", 5.0)
        ), 3.0);

        // expectedThreshold = expectedTarget (30.0). Deadline far away → GREEN, not YELLOW.
        assertProgress(progress, 30.0, 30.0, 24.0, 80.0, PerformanceStatusClassifier.GREEN);
    }

    @Test
    void treatsAnnualAsSinglePeriod() {
        KpiDefinition kpi = kpiDefinition(ReportingFrequency.ANNUAL, 100.0, 80.0);

        KpiPeriodProgress progress = KpiPeriodProgressCalculator.calculate(kpi, "2026", List.of(), 79.0);

        // expectedThreshold = expectedTarget (100.0). Deadline far away, 79% progress → GREEN, not RED.
        assertProgress(progress, 100.0, 100.0, 79.0, 79.0, PerformanceStatusClassifier.GREEN);
    }

    @Test
    void treatsOneTimeAsSinglePeriod() {
        KpiDefinition kpi = kpiDefinition(ReportingFrequency.ONE_TIME, 100.0, 80.0);

        KpiPeriodProgress progress = KpiPeriodProgressCalculator.calculate(
                kpi,
                "Due by Dec 31, 2026",
                List.of(),
                100.0
        );

        // expectedThreshold = expectedTarget (100.0). cumulative=100 meets target → GREEN.
        assertProgress(progress, 100.0, 100.0, 100.0, 100.0, PerformanceStatusClassifier.GREEN);
    }

    private static KpiDefinition kpiDefinition(ReportingFrequency frequency, double target, double threshold) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setReportingFrequency(frequency);
        kpi.setTargetValue(target);
        kpi.setThreshold(threshold);
        kpi.setDeadline(LocalDate.of(2026, 12, 31));
        kpi.setDateCreated(LocalDateTime.of(2026, 1, 1, 0, 0));
        return kpi;
    }

    private static KpiSubmission submission(String reportingPeriod, double submittedValue) {
        KpiSubmission submission = new KpiSubmission();
        submission.setReportingPeriod(reportingPeriod);
        submission.setSubmittedValue(submittedValue);
        return submission;
    }

    private static void assertProgress(
            KpiPeriodProgress progress,
            double expectedTarget,
            double expectedThreshold,
            double cumulativeSubmittedValue,
            double achievementRate,
            String performanceStatus
    ) {
        assertEquals(expectedTarget, progress.expectedTarget());
        assertEquals(expectedThreshold, progress.expectedThreshold());
        assertEquals(cumulativeSubmittedValue, progress.cumulativeSubmittedValue());
        assertEquals(achievementRate, progress.achievementRate());
        assertEquals(performanceStatus, progress.performanceStatus());
    }
}
