package edu.cit.dasig_core.features.kpisubmission.util;

public final class PerformanceStatusClassifier {

    public static final String GREEN = "GREEN";
    public static final String YELLOW = "YELLOW";
    public static final String RED = "RED";

    private PerformanceStatusClassifier() {
    }

    /**
     * Classifies performance using the KPI threshold as the minimum acceptable achievement percentage.
     *
     * @param achievementRate computed percentage from {@link KpiAchievementCalculator}
     * @param threshold       minimum acceptable achievement percentage configured on the KPI
     */
    public static String classify(double achievementRate, double threshold) {
        if (achievementRate >= 100) {
            return GREEN;
        }
        if (achievementRate >= threshold) {
            return YELLOW;
        }
        return RED;
    }
}
