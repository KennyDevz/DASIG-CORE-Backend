package edu.cit.dasig_core.core.event;

/** KPI events that send an email (UC-4.5). */
public enum KpiEmailType {
    /** New KPI assigned to a committee: emails its Leads and Members. */
    KPI_ASSIGNED,
    /** KPI deadline changed: emails the committee's Leads and Members. */
    DEADLINE_UPDATED,
    /** New At Risk alert: emails the committee's Leads and Members (Admins get the alert email). */
    KPI_AT_RISK,
    /** New Overdue alert: emails the committee's Leads and Members (Admins get the alert email). */
    KPI_OVERDUE,
    /** Member submitted progress: emails the committee's Leads for review. */
    SUBMISSION_SENT,
    /** Lead approved or rejected a Member's submission: emails that Member. */
    SUBMISSION_REVIEWED,
    /** A final value now counts toward official progress: emails the Admins. */
    FINAL_VALUE_APPROVED
}
