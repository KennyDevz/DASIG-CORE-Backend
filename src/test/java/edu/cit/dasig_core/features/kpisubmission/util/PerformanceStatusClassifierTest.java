package edu.cit.dasig_core.features.kpisubmission.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceStatusClassifierTest {

    @Test
    void classify_green_whenTargetFullyReachedEvenPastDeadline() {
        String status = PerformanceStatusClassifier.classify(100.0, 100.0, LocalDate.now().minusDays(1));

        assertThat(status).isEqualTo(PerformanceStatusClassifier.GREEN);
    }

    @Test
    void classify_green_whenTargetExceeded() {
        String status = PerformanceStatusClassifier.classify(150.0, 100.0, LocalDate.now().plusMonths(6));

        assertThat(status).isEqualTo(PerformanceStatusClassifier.GREEN);
    }

    @Test
    void classify_red_whenDeadlinePassedAndTargetNotReached() {
        String status = PerformanceStatusClassifier.classify(50.0, 100.0, LocalDate.now().minusDays(1));

        assertThat(status).isEqualTo(PerformanceStatusClassifier.RED);
    }

    @Test
    void classify_yellow_whenDeadlineSoonAndProgressBelowFiftyPercent() {
        String status = PerformanceStatusClassifier.classify(20.0, 100.0, LocalDate.now().plusDays(30));

        assertThat(status).isEqualTo(PerformanceStatusClassifier.YELLOW);
    }

    @Test
    void classify_green_whenDeadlineSoonButProgressAtLeastFiftyPercent() {
        String status = PerformanceStatusClassifier.classify(50.0, 100.0, LocalDate.now().plusDays(30));

        assertThat(status).isEqualTo(PerformanceStatusClassifier.GREEN);
    }

    @Test
    void classify_green_whenDeadlineFarAwayRegardlessOfLowProgress() {
        String status = PerformanceStatusClassifier.classify(5.0, 100.0, LocalDate.now().plusDays(90));

        assertThat(status).isEqualTo(PerformanceStatusClassifier.GREEN);
    }

    @Test
    void classify_atExactSixtyDayBoundary_appliesAtRiskRule() {
        String status = PerformanceStatusClassifier.classify(10.0, 100.0, LocalDate.now().plusDays(60));

        assertThat(status).isEqualTo(PerformanceStatusClassifier.YELLOW);
    }

    @Test
    void classify_zeroTarget_neverReportsGreenFromGoalCompletion() {
        // A KPI with targetValue = 0 is a data-entry error (CreateKpiDefinitionRequest only
        // has @NotNull, not @Positive, so 0 is currently accepted). With this deadline-based
        // model, a zero target no longer short-circuits to GREEN via goal completion (the
        // `targetValue > 0` guard blocks that) - it instead falls through to the deadline
        // rules below, which is more defensible than the old model but still somewhat
        // meaningless for a target that was never valid to begin with.
        String overdue = PerformanceStatusClassifier.classify(0.0, 0.0, LocalDate.now().minusDays(1));
        String upcoming = PerformanceStatusClassifier.classify(0.0, 0.0, LocalDate.now().plusDays(90));

        assertThat(overdue).isEqualTo(PerformanceStatusClassifier.RED);
        assertThat(upcoming).isEqualTo(PerformanceStatusClassifier.GREEN);
    }
}
