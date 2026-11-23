package edu.cit.dasig_core.features.activity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActivityLogFilterTest {

    @Test
    void actionFor_matchesValidationTaskEndpoints() {
        assertEquals("KPI_CREATED", ActivityLogFilter.actionFor("POST", "/api/kpi-definitions"));
        assertEquals("ALERT_ACKNOWLEDGED", ActivityLogFilter.actionFor("PATCH", "/api/alerts/41/acknowledge"));
        assertEquals("REPORT_GENERATED", ActivityLogFilter.actionFor("POST", "/api/reports/generate/committee"));
        assertEquals("REPORT_GENERATED", ActivityLogFilter.actionFor("POST", "/api/reports/generate/kpi"));
        assertEquals("REPORT_EXPORTED", ActivityLogFilter.actionFor("GET", "/api/reports/PR-2026-0012/export"));
        assertEquals("SUBMISSION_REVIEWED", ActivityLogFilter.actionFor("PATCH", "/api/kpi-submissions/305/review"));
        assertEquals("SUBMISSION_CREATED", ActivityLogFilter.actionFor("POST", "/api/kpi-submissions"));
        assertEquals("OVERDUE_EXPLANATION_ADDED",
                ActivityLogFilter.actionFor("POST", "/api/committee-alerts/12/explanation"));
    }

    @Test
    void actionFor_ignoresOtherRequests() {
        assertNull(ActivityLogFilter.actionFor("GET", "/api/kpi-definitions"));
        assertNull(ActivityLogFilter.actionFor("PUT", "/api/kpi-definitions/5"));
        assertNull(ActivityLogFilter.actionFor("PATCH", "/api/kpi-submissions/mark-viewed"));
        assertNull(ActivityLogFilter.actionFor("GET", "/api/reports/7"));
    }
}
