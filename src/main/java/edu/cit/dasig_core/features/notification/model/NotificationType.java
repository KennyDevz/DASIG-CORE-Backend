package edu.cit.dasig_core.features.notification.model;

public enum NotificationType {
    SEVEN_DAYS_BEFORE(7),
    TWO_DAYS_BEFORE(2);

    private final int daysBeforeDeadline;

    NotificationType(int daysBeforeDeadline) {
        this.daysBeforeDeadline = daysBeforeDeadline;
    }

    public int getDaysBeforeDeadline() {
        return daysBeforeDeadline;
    }
}
