package edu.cit.dasig_core.features.kpisubmission.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KpiAchievementCalculatorTest {

    @Test
    void calculate_returnsZeroWhenTargetIsZero() {
        assertThat(KpiAchievementCalculator.calculate(50.0, 0.0)).isZero();
    }

    @Test
    void calculate_returnsHundredWhenSubmittedEqualsTarget() {
        assertThat(KpiAchievementCalculator.calculate(100.0, 100.0)).isEqualTo(100.0);
    }

    @Test
    void calculate_returnsPercentageOfTarget() {
        assertThat(KpiAchievementCalculator.calculate(25.0, 50.0)).isEqualTo(50.0);
    }

    @Test
    void calculate_canExceedHundredWhenOverachieved() {
        assertThat(KpiAchievementCalculator.calculate(150.0, 100.0)).isEqualTo(150.0);
    }

    @Test
    void calculate_isZeroWhenNothingSubmittedYet() {
        assertThat(KpiAchievementCalculator.calculate(0.0, 100.0)).isZero();
    }

    @Test
    void calculate_roundsToTwoDecimalPlacesHalfUp() {
        // 1/3 * 100 = 33.3333... -> rounds to 33.33
        assertThat(KpiAchievementCalculator.calculate(1.0, 3.0)).isEqualTo(33.33);
    }
}
