package edu.cit.dasig_core.features.kpisubmission.util;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.util.ReportingPeriodResolver;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;

import java.time.LocalDate;
import java.util.List;

/**
 * Calculates cumulative KPI progress and classifies performance status.
 *
 * <p>For ONE_TIME KPIs (Submit Anytime workflow), there is exactly one reporting
 * period. Progress is cumulative across all submissions, compared against the
 * overall target value. Status is determined by deadline proximity and goal
 * completion rather than periodic thresholds.</p>
 */
public final class KpiPeriodProgressCalculator {

    private KpiPeriodProgressCalculator() {
    }

    public static KpiPeriodProgress calculate(
            KpiDefinition kpiDefinition,
            String reportingPeriod,
            List<KpiSubmission> submissions,
            double currentSubmittedValue
    ) {
        LocalDate assignmentStart = resolveAssignmentStart(kpiDefinition);
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                kpiDefinition.getReportingFrequency(),
                kpiDefinition.getDeadline(),
                assignmentStart
        );

        int zeroBasedPeriodIndex = periods.indexOf(reportingPeriod);
        if (zeroBasedPeriodIndex < 0) {
            throw new IllegalArgumentException("Invalid reporting period for this KPI.");
        }

        int periodNumber = zeroBasedPeriodIndex + 1;
        int periodCount = periods.size();
        double progressRatio = (double) periodNumber / periodCount;
        // expectedTarget is the proportional target for this period in periodic KPIs,
        // or just the full targetValue for ONE_TIME KPIs (periodCount == 1).
        double expectedTarget = kpiDefinition.getTargetValue() * progressRatio;

        double cumulativeSubmittedValue = currentSubmittedValue + sumPreviousPeriodValues(
                submissions,
                periods,
                zeroBasedPeriodIndex
        );

        double achievementRate = KpiAchievementCalculator.calculate(cumulativeSubmittedValue, expectedTarget);

        // Use deadline-paced classification against overall target
        String performanceStatus = PerformanceStatusClassifier.classify(
                cumulativeSubmittedValue,
                kpiDefinition.getTargetValue(),
                kpiDefinition.getDeadline()
        );

        return new KpiPeriodProgress(
                expectedTarget,
                // expectedThreshold is kept for API compatibility but is now equal to expectedTarget
                expectedTarget,
                cumulativeSubmittedValue,
                achievementRate,
                performanceStatus
        );
    }

    public static KpiPeriodProgress calculateExisting(
            KpiDefinition kpiDefinition,
            String reportingPeriod,
            List<KpiSubmission> submissions
    ) {
        double currentSubmittedValue = submissions.stream()
                .filter(submission -> reportingPeriod.equals(submission.getReportingPeriod()))
                .mapToDouble(KpiSubmission::getSubmittedValue)
                .sum();

        return calculate(kpiDefinition, reportingPeriod, submissions, currentSubmittedValue);
    }

    public static KpiPeriodProgress calculateWithNewSubmission(
            KpiDefinition kpiDefinition,
            String reportingPeriod,
            List<KpiSubmission> existingSubmissions,
            double newSubmittedValue
    ) {
        double existingCurrentPeriodValue = existingSubmissions.stream()
                .filter(submission -> reportingPeriod.equals(submission.getReportingPeriod()))
                .mapToDouble(KpiSubmission::getSubmittedValue)
                .sum();

        return calculate(
                kpiDefinition,
                reportingPeriod,
                existingSubmissions,
                existingCurrentPeriodValue + newSubmittedValue
        );
    }

    private static double sumPreviousPeriodValues(
            List<KpiSubmission> submissions,
            List<String> periods,
            int currentPeriodIndex
    ) {
        return submissions.stream()
                .filter(submission -> {
                    int submissionPeriodIndex = periods.indexOf(submission.getReportingPeriod());
                    return submissionPeriodIndex >= 0 && submissionPeriodIndex < currentPeriodIndex;
                })
                .mapToDouble(KpiSubmission::getSubmittedValue)
                .sum();
    }

    private static LocalDate resolveAssignmentStart(KpiDefinition kpiDefinition) {
        return kpiDefinition.getDateCreated() != null
                ? kpiDefinition.getDateCreated().toLocalDate()
                : kpiDefinition.getDeadline();
    }

    public record KpiPeriodProgress(
            double expectedTarget,
            double expectedThreshold,
            double cumulativeSubmittedValue,
            double achievementRate,
            String performanceStatus
    ) {
    }
}
