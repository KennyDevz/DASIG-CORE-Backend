package edu.cit.dasig_core.features.notification.model;

/**
 * Types of in-app notifications sent to Committee Leads and Members (UC-4.3).
 * Only the deadline reminders carry a day count; the alert-related types do not.
 */
public enum NotificationType {
    SEVEN_DAYS_BEFORE(7),
    TWO_DAYS_BEFORE(2),
    AT_RISK(null),
    OVERDUE(null),
    ALERT_ACKNOWLEDGED(null),
    DEADLINE_UPDATED(null);

    private final Integer daysBeforeDeadline;

    NotificationType(Integer daysBeforeDeadline) {
        this.daysBeforeDeadline = daysBeforeDeadline;
    }

    /** Days before the deadline for reminder types; {@code null} for every other type. */
    public Integer getDaysBeforeDeadline() {
        return daysBeforeDeadline;
    }

    public boolean isDeadlineReminder() {
        return daysBeforeDeadline != null;
    }
}
