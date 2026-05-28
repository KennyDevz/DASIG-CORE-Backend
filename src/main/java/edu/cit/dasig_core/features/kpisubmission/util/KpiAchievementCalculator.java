package edu.cit.dasig_core.features.kpisubmission.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class KpiAchievementCalculator {

    private KpiAchievementCalculator() {
    }

    /**
     * Computes achievement rate as a percentage of the KPI target.
     *
     * @return percentage in the range [0, +inf); 0 when target is zero
     */
    public static double calculate(double submittedValue, double targetValue) {
        if (targetValue == 0) {
            return 0.0;
        }

        return BigDecimal.valueOf(submittedValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(targetValue), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
