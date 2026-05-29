package edu.cit.dasig_core.features.kpi.util;

import edu.cit.dasig_core.features.kpi.model.ReportingFrequency;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReportingPeriodResolver {

    private static final DateTimeFormatter ONE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTHLY_FORMAT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private ReportingPeriodResolver() {
    }

    public static List<String> generatePeriodOptions(
            ReportingFrequency frequency,
            LocalDate deadline,
            LocalDate assignmentStart
    ) {
        LocalDate start = assignmentStart != null ? assignmentStart : LocalDate.now();
        if (deadline == null) {
            return List.of();
        }

        return switch (frequency != null ? frequency : ReportingFrequency.QUARTERLY) {
            case ONE_TIME -> List.of(formatOneTime(deadline));
            case QUARTERLY -> generateQuarterly(start, deadline);
            case ANNUAL -> generateAnnual(start, deadline);
            case MONTHLY -> generateMonthly(start, deadline);
        };
    }

    public static String resolveCurrentPeriod(
            ReportingFrequency frequency,
            LocalDate deadline,
            LocalDate assignmentStart,
            LocalDate asOf
    ) {
        List<String> options = generatePeriodOptions(frequency, deadline, assignmentStart);
        if (options.isEmpty()) {
            return null;
        }

        ReportingFrequency resolvedFrequency =
                frequency != null ? frequency : ReportingFrequency.QUARTERLY;
        LocalDate referenceDate = asOf != null ? asOf : LocalDate.now();
        if (referenceDate.isAfter(deadline)) {
            referenceDate = deadline;
        }

        String candidate = switch (resolvedFrequency) {
            case ONE_TIME -> formatOneTime(deadline);
            case QUARTERLY -> formatQuarterFromMonth(referenceDate.getMonthValue(), referenceDate.getYear());
            case ANNUAL -> String.valueOf(referenceDate.getYear());
            case MONTHLY -> referenceDate.format(MONTHLY_FORMAT);
        };

        if (options.contains(candidate)) {
            return candidate;
        }

        String fallback = null;
        for (String option : options) {
            fallback = option;
        }
        return fallback;
    }

    public static boolean isValidPeriod(
            ReportingFrequency frequency,
            LocalDate deadline,
            LocalDate assignmentStart,
            String reportingPeriod
    ) {
        if (reportingPeriod == null || reportingPeriod.isBlank()) {
            return false;
        }
        return generatePeriodOptions(frequency, deadline, assignmentStart)
                .contains(reportingPeriod);
    }

    private static String formatOneTime(LocalDate deadline) {
        return "Due by " + deadline.format(ONE_TIME_FORMAT);
    }

    private static List<String> generateQuarterly(LocalDate start, LocalDate deadline) {
        List<String> periods = new ArrayList<>();
        int startYear = start.getYear();
        int endYear = deadline.getYear();

        for (int year = startYear; year <= endYear; year++) {
            for (int quarter = 1; quarter <= 4; quarter++) {
                LocalDate quarterEnd = quarterEndDate(year, quarter);
                if (!quarterEnd.isBefore(start) && !quarterEnd.isAfter(deadline)) {
                    periods.add(formatQuarter(quarter, year));
                }
            }
        }
        return periods;
    }

    private static List<String> generateAnnual(LocalDate start, LocalDate deadline) {
        List<String> periods = new ArrayList<>();
        for (int year = start.getYear(); year <= deadline.getYear(); year++) {
            periods.add(String.valueOf(year));
        }
        return periods;
    }

    private static List<String> generateMonthly(LocalDate start, LocalDate deadline) {
        List<String> periods = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth end = YearMonth.from(deadline);

        while (!cursor.isAfter(end)) {
            periods.add(cursor.atDay(1).format(MONTHLY_FORMAT));
            cursor = cursor.plusMonths(1);
        }
        return periods;
    }

    private static String formatQuarterFromMonth(int month, int year) {
        int quarter = ((month - 1) / 3) + 1;
        return formatQuarter(quarter, year);
    }

    private static String formatQuarter(int quarter, int year) {
        return "Q" + quarter + " " + year;
    }

    private static LocalDate quarterEndDate(int year, int quarter) {
        Month endMonth = Month.of(quarter * 3);
        return YearMonth.of(year, endMonth).atEndOfMonth();
    }
}
