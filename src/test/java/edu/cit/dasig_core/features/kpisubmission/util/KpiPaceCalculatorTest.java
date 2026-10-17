package edu.cit.dasig_core.features.kpisubmission.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class KpiPaceCalculatorTest {

    // 120-day KPI: Jan 1 -> May 1, 2026, target 100
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEADLINE = LocalDate.of(2026, 5, 1);
    private static final LocalDate MONTH_1 = LocalDate.of(2026, 1, 31); // 25% passed, expects 25
    private static final LocalDate MONTH_2 = LocalDate.of(2026, 3, 2);  // 50% passed, expects 50

    @Test
    void expectedByNow_growsEvenlyOverTimeline() {
        assertThat(KpiPaceCalculator.expectedByNow(100, START, DEADLINE, MONTH_1)).isEqualTo(25.0);
        assertThat(KpiPaceCalculator.expectedByNow(100, START, DEADLINE, MONTH_2)).isEqualTo(50.0);
        assertThat(KpiPaceCalculator.expectedByNow(100, START, DEADLINE, DEADLINE.plusDays(5))).isEqualTo(100.0);
    }

    @Test
    void atRisk_whenBelowHalfOfExpected() {
        assertThat(KpiPaceCalculator.isAtRisk(12, 100, START, DEADLINE, MONTH_1)).isTrue();
        assertThat(KpiPaceCalculator.isAtRisk(24, 100, START, DEADLINE, MONTH_2)).isTrue();
    }

    @Test
    void notAtRisk_whenAtOrAboveHalfOfExpected() {
        assertThat(KpiPaceCalculator.isAtRisk(12.5, 100, START, DEADLINE, MONTH_1)).isFalse();
        assertThat(KpiPaceCalculator.isAtRisk(25, 100, START, DEADLINE, MONTH_2)).isFalse();
    }

    @Test
    void notAtRisk_beforeQuarterOfTimePassed() {
        assertThat(KpiPaceCalculator.isAtRisk(0, 100, START, DEADLINE, START.plusDays(29))).isFalse();
    }

    @Test
    void notAtRisk_whenDatesOrTargetInvalid() {
        assertThat(KpiPaceCalculator.isAtRisk(0, 100, null, DEADLINE, MONTH_2)).isFalse();
        assertThat(KpiPaceCalculator.isAtRisk(0, 100, START, null, MONTH_2)).isFalse();
        assertThat(KpiPaceCalculator.isAtRisk(0, 100, DEADLINE, DEADLINE, DEADLINE)).isFalse();
        assertThat(KpiPaceCalculator.isAtRisk(0, 0, START, DEADLINE, MONTH_2)).isFalse();
    }
}
