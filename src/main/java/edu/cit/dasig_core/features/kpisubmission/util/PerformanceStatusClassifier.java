package edu.cit.dasig_core.features.kpisubmission.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Classifies KPI performance based on goal completion and deadline proximity.
 *
 * <p>Replaces the old threshold-based classification that assumed strict
 * periodic quotas. The new system uses the "Submit Anytime" model where users
 * submit progress freely before the deadline, and status reflects whether the
 * overall goal is on pace given the remaining time.</p>
 *
 * <p>Rules (Option 1 - Goal and Deadline-Based):
 * <ul>
 *   <li>COMPLETED - cumulativeValue &gt;= targetValue (goal fully reached)</li>
 *   <li>OVERDUE   - deadline has passed AND goal not reached</li>
 *   <li>AT_RISK   - deadline is within AT_RISK_DAYS AND progress &lt; AT_RISK_RATIO of target</li>
 *   <li>ON_TRACK  - otherwise (active, progressing, time remaining)</li>
 * </ul>
 */
public final class PerformanceStatusClassifier {

    public static final String COMPLETED = "COMPLETED";
    public static final String ON_TRACK  = "ON_TRACK";
    public static final String AT_RISK   = "AT_RISK";
    public static final String OVERDUE   = "OVERDUE";

    /** Number of days before the deadline that triggers AT_RISK if progress is low. */
    private static final long   AT_RISK_DAYS  = 60L;
    /** Progress ratio (0-1) below which a near-deadline KPI is flagged AT_RISK. */
    private static final double AT_RISK_RATIO = 0.50;

    private PerformanceStatusClassifier() {
    }

    /**
     * Classifies performance using overall goal completion and deadline proximity.
     *
     * @param cumulativeSubmittedValue total value submitted so far (across all periods)
     * @param targetValue              overall KPI target value
     * @param deadline                 KPI deadline date
     * @return COMPLETED, ON_TRACK, AT_RISK, or OVERDUE
     */
    public static String classify(double cumulativeSubmittedValue, double targetValue, LocalDate deadline) {
        // Goal fully reached -> always COMPLETED
        if (targetValue > 0 && cumulativeSubmittedValue >= targetValue) {
            return COMPLETED;
        }

        long daysUntilDeadline = ChronoUnit.DAYS.between(LocalDate.now(), deadline);

        // Deadline passed, goal not reached -> OVERDUE
        if (daysUntilDeadline < 0) {
            return OVERDUE;
        }

        // Deadline approaching and progress is significantly low -> AT_RISK
        double progressRatio = targetValue > 0 ? cumulativeSubmittedValue / targetValue : 0.0;
        if (daysUntilDeadline <= AT_RISK_DAYS && progressRatio < AT_RISK_RATIO) {
            return AT_RISK;
        }

        // Actively progressing with time remaining -> ON_TRACK
        return ON_TRACK;
    }
}
