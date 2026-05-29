package edu.cit.dasig_core.features.kpisubmission.util;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.kpi.util.ReportingPeriodResolver;
import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;

import java.time.LocalDate;
import java.util.List;

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
        double expectedTarget = kpiDefinition.getTargetValue() * progressRatio;
        double expectedThreshold = kpiDefinition.getThreshold() * progressRatio;
        double cumulativeSubmittedValue = currentSubmittedValue + sumPreviousPeriodValues(
                submissions,
                periods,
                zeroBasedPeriodIndex
        );
        double achievementRate = KpiAchievementCalculator.calculate(cumulativeSubmittedValue, expectedTarget);
        String performanceStatus = PerformanceStatusClassifier.classify(
                cumulativeSubmittedValue,
                expectedTarget,
                expectedThreshold
        );

        return new KpiPeriodProgress(
                expectedTarget,
                expectedThreshold,
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
                .findFirst()
                .orElse(0.0);

        return calculate(kpiDefinition, reportingPeriod, submissions, currentSubmittedValue);
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
