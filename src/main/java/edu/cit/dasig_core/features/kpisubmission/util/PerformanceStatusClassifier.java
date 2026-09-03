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
 *   <li>GREEN  - cumulativeValue &gt;= targetValue (goal fully reached)</li>
 *   <li>RED    - deadline has passed AND goal not reached (truly overdue)</li>
 *   <li>YELLOW - deadline is within AT_RISK_DAYS AND progress &lt; AT_RISK_RATIO of target</li>
 *   <li>GREEN  - otherwise (active, progressing, time remaining)</li>
 * </ul>
 */
public final class PerformanceStatusClassifier {

    public static final String GREEN  = "GREEN";
    public static final String YELLOW = "YELLOW";
    public static final String RED    = "RED";

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
     * @return GREEN, YELLOW, or RED
     */
    public static String classify(double cumulativeSubmittedValue, double targetValue, LocalDate deadline) {
        // Goal fully reached -> always GREEN
        if (targetValue > 0 && cumulativeSubmittedValue >= targetValue) {
            return GREEN;
        }

        long daysUntilDeadline = ChronoUnit.DAYS.between(LocalDate.now(), deadline);

        // Deadline passed, goal not reached -> RED (Overdue)
        if (daysUntilDeadline < 0) {
            return RED;
        }

        // Deadline approaching and progress is significantly low -> YELLOW (At Risk)
        double progressRatio = targetValue > 0 ? cumulativeSubmittedValue / targetValue : 0.0;
        if (daysUntilDeadline <= AT_RISK_DAYS && progressRatio < AT_RISK_RATIO) {
            return YELLOW;
        }

        // Actively progressing with time remaining -> GREEN (On Track / In Progress)
        return GREEN;
    }
}
