package edu.cit.dasig_core.features.notification.model;

import java.time.LocalDate;

/**
 * Builds the {@code dedupe_key} values that keep notifications unique per KPI and organization.
 */
public final class NotificationKeys {

    private NotificationKeys() {
    }

    /** Deadline reminders: one per reminder type per deadline, so changing the deadline allows new reminders. */
    public static String deadlineReminder(NotificationType type, LocalDate deadline) {
        return type.name() + ":" + deadline;
    }

    /** At Risk, Overdue and Alert Acknowledged: one per alert. */
    public static String forAlert(NotificationType type, Long alertId) {
        return type.name() + ":alert-" + alertId;
    }

    /** Deadline Updated: one per deadline change. */
    public static String deadlineUpdated(LocalDate oldDeadline, LocalDate newDeadline) {
        return NotificationType.DEADLINE_UPDATED.name() + ":" + oldDeadline + "->" + newDeadline;
    }
}
