package edu.cit.dasig_core.features.kpisubmission.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * At Risk rule for KPI alerts.
 * A KPI is At Risk when at least 25% of its time (creation date to deadline) has passed
 * and its progress is below 50% of what it should have reached by now.
 */
public final class KpiPaceCalculator {

    private KpiPaceCalculator() {
    }

    /** Progress the KPI should have reached by today. Returns 0 if dates are missing or invalid. */
    public static double expectedByNow(double target, LocalDate startDate, LocalDate deadline, LocalDate today) {
        if (startDate == null || deadline == null || !deadline.isAfter(startDate)) {
            return 0.0;
        }
        return target * elapsedRatio(startDate, deadline, today);
    }

    /** True when the KPI is behind pace. */
    public static boolean isAtRisk(double actual, double target, LocalDate startDate, LocalDate deadline, LocalDate today) {
        if (startDate == null || deadline == null || target <= 0 || !deadline.isAfter(startDate)) {
            return false;
        }
        boolean quarterPassed = elapsedRatio(startDate, deadline, today) >= 0.25;
        return quarterPassed && actual < 0.5 * expectedByNow(target, startDate, deadline, today);
    }

    /** Share of the timeline that has passed, from 0.0 to 1.0. */
    private static double elapsedRatio(LocalDate startDate, LocalDate deadline, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(startDate, deadline);
        long daysPassed = ChronoUnit.DAYS.between(startDate, today);
        return Math.min(1.0, Math.max(0.0, (double) daysPassed / totalDays));
    }
}
