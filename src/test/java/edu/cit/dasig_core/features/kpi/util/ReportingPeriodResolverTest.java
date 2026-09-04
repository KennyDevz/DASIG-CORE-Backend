package edu.cit.dasig_core.features.kpi.util;

import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingPeriodResolverTest {

    @Test
    void generatePeriodOptions_returnsEmptyListWhenDeadlineIsNull() {
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                ReportingFrequency.QUARTERLY, null, LocalDate.of(2026, 1, 1));

        assertThat(periods).isEmpty();
    }

    @Test
    void generatePeriodOptions_oneTime_returnsSingleDueByEntry() {
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                ReportingFrequency.ONE_TIME, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));

        assertThat(periods).containsExactly("Due by Dec 31, 2026");
    }

    @Test
    void generatePeriodOptions_quarterly_spansFullYearWhenStartAndDeadlineAlignToYearBoundaries() {
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                ReportingFrequency.QUARTERLY, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));

        assertThat(periods).containsExactly("Q1 2026", "Q2 2026", "Q3 2026", "Q4 2026");
    }

    @Test
    void generatePeriodOptions_quarterly_spansAcrossYears() {
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                ReportingFrequency.QUARTERLY, LocalDate.of(2027, 6, 30), LocalDate.of(2026, 10, 1));

        assertThat(periods).containsExactly("Q4 2026", "Q1 2027", "Q2 2027");
    }

    @Test
    void generatePeriodOptions_quarterly_excludesQuarterWhoseEndFallsAfterAMidQuarterDeadline() {
        // KNOWN BUG (found during backend review): a quarter is only included when its END
        // date falls on/before the deadline. A deadline that lands mid-quarter (not exactly
        // on a quarter boundary) excludes that quarter entirely instead of including the
        // partial quarter it actually falls within. For a KPI created and due within the
        // same quarter, this produces an EMPTY period list, making the KPI unsubmittable.
        // This test documents current behavior; update it if/when the underlying logic
        // in ReportingPeriodResolver.generateQuarterly is fixed to include the deadline's
        // own quarter.
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                ReportingFrequency.QUARTERLY, LocalDate.of(2026, 2, 15), LocalDate.of(2026, 1, 1));

        assertThat(periods).isEmpty();
    }

    @Test
    void generatePeriodOptions_annual_listsEveryYearInRange() {
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                ReportingFrequency.ANNUAL, LocalDate.of(2028, 6, 1), LocalDate.of(2026, 3, 1));

        assertThat(periods).containsExactly("2026", "2027", "2028");
    }

    @Test
    void generatePeriodOptions_monthly_listsEveryMonthInRange() {
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                ReportingFrequency.MONTHLY, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 1, 15));

        assertThat(periods).containsExactly("Jan 2026", "Feb 2026", "Mar 2026");
    }

    @Test
    void generatePeriodOptions_defaultsToQuarterlyWhenFrequencyIsNull() {
        List<String> periods = ReportingPeriodResolver.generatePeriodOptions(
                null, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));

        assertThat(periods).containsExactly("Q1 2026", "Q2 2026", "Q3 2026", "Q4 2026");
    }

    @Test
    void resolveCurrentPeriod_returnsNullWhenNoPeriodsExist() {
        String current = ReportingPeriodResolver.resolveCurrentPeriod(
                ReportingFrequency.QUARTERLY,
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 10));

        assertThat(current).isNull();
    }

    @Test
    void resolveCurrentPeriod_returnsPeriodMatchingTheReferenceDate() {
        String current = ReportingPeriodResolver.resolveCurrentPeriod(
                ReportingFrequency.QUARTERLY,
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 15)); // May -> Q2

        assertThat(current).isEqualTo("Q2 2026");
    }

    @Test
    void resolveCurrentPeriod_clampsReferenceDateToDeadlineWhenAsOfIsAfterDeadline() {
        String current = ReportingPeriodResolver.resolveCurrentPeriod(
                ReportingFrequency.QUARTERLY,
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 6, 1)); // well past the deadline

        // Clamped reference date is the deadline itself (Dec 31, 2026 -> Q4)
        assertThat(current).isEqualTo("Q4 2026");
    }

    @Test
    void isValidPeriod_falseForNullOrBlankPeriod() {
        assertThat(ReportingPeriodResolver.isValidPeriod(
                ReportingFrequency.QUARTERLY, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), null)).isFalse();
        assertThat(ReportingPeriodResolver.isValidPeriod(
                ReportingFrequency.QUARTERLY, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), "  ")).isFalse();
    }

    @Test
    void isValidPeriod_trueOnlyForAGeneratedOption() {
        assertThat(ReportingPeriodResolver.isValidPeriod(
                ReportingFrequency.QUARTERLY, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), "Q2 2026")).isTrue();
        assertThat(ReportingPeriodResolver.isValidPeriod(
                ReportingFrequency.QUARTERLY, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), "Q1 2099")).isFalse();
    }
}
