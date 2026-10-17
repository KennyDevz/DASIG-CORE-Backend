package edu.cit.dasig_core.features.kpisubmission.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceStatusClassifierTest {

    // 120-day KPI: Jan 1 -> May 1, 2026, target 100
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEADLINE = LocalDate.of(2026, 5, 1);
    private static final LocalDate MONTH_2 = LocalDate.of(2026, 3, 2); // 50% passed, expects 50

    @Test
    void completed_whenTargetReached_evenAfterDeadline() {
        assertThat(PerformanceStatusClassifier.classify(100, 100, START, DEADLINE, DEADLINE.plusDays(1)))
                .isEqualTo(PerformanceStatusClassifier.COMPLETED);
    }

    @Test
    void overdue_whenDeadlinePassedAndTargetNotReached() {
        assertThat(PerformanceStatusClassifier.classify(50, 100, START, DEADLINE, DEADLINE.plusDays(1)))
                .isEqualTo(PerformanceStatusClassifier.OVERDUE);
    }

    @Test
    void atRisk_whenBelowHalfOfExpectedPace() {
        assertThat(PerformanceStatusClassifier.classify(24, 100, START, DEADLINE, MONTH_2))
                .isEqualTo(PerformanceStatusClassifier.AT_RISK);
    }

    @Test
    void onTrack_whenAtOrAboveHalfOfExpectedPace() {
        assertThat(PerformanceStatusClassifier.classify(25, 100, START, DEADLINE, MONTH_2))
                .isEqualTo(PerformanceStatusClassifier.ON_TRACK);
    }

    @Test
    void onTrack_earlyInTimeline_evenWithNoProgress() {
        assertThat(PerformanceStatusClassifier.classify(0, 100, START, DEADLINE, START.plusDays(20)))
                .isEqualTo(PerformanceStatusClassifier.ON_TRACK);
    }

    @Test
    void onTrack_whenCreationDateMissing() {
        assertThat(PerformanceStatusClassifier.classify(0, 100, null, DEADLINE, MONTH_2))
                .isEqualTo(PerformanceStatusClassifier.ON_TRACK);
    }
}
