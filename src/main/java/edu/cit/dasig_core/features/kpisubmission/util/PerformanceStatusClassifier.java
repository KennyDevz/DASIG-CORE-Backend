package edu.cit.dasig_core.features.kpisubmission.util;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Classifies KPI performance for the dashboard, submissions and reports.
 *
 * <p>Rules, checked in this order (same At Risk rule as KPI alerts):
 * <ul>
 *   <li>COMPLETED - cumulative value &gt;= target</li>
 *   <li>OVERDUE   - deadline has passed and target not reached</li>
 *   <li>AT_RISK   - behind pace, see {@link KpiPaceCalculator}: at least 25% of the time
 *                   (creation date to deadline) has passed and progress is below 50% of
 *                   what is expected by now</li>
 *   <li>ON_TRACK  - otherwise</li>
 * </ul>
 */
public final class PerformanceStatusClassifier {

    public static final String COMPLETED = "COMPLETED";
    public static final String ON_TRACK  = "ON_TRACK";
    public static final String AT_RISK   = "AT_RISK";
    public static final String OVERDUE   = "OVERDUE";

    /** Business time zone used to decide what "today" is. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Manila");

    private PerformanceStatusClassifier() {
    }

    /** Today's date in the business time zone (Asia/Manila). */
    public static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /** Classifies performance as of today (Asia/Manila). */
    public static String classify(double cumulativeValue, double targetValue, LocalDate startDate, LocalDate deadline) {
        return classify(cumulativeValue, targetValue, startDate, deadline, today());
    }

    /** Classifies performance as of the given date (used by tests). */
    public static String classify(
            double cumulativeValue,
            double targetValue,
            LocalDate startDate,
            LocalDate deadline,
            LocalDate today
    ) {
        if (targetValue > 0 && cumulativeValue >= targetValue) {
            return COMPLETED;
        }
        if (deadline != null && today.isAfter(deadline)) {
            return OVERDUE;
        }
        if (KpiPaceCalculator.isAtRisk(cumulativeValue, targetValue, startDate, deadline, today)) {
            return AT_RISK;
        }
        return ON_TRACK;
    }
}
