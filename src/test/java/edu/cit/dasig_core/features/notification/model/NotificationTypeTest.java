package edu.cit.dasig_core.features.notification.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeTest {

    @Test
    void deadlineReminders_haveDayCounts() {
        assertThat(NotificationType.SEVEN_DAYS_BEFORE.isDeadlineReminder()).isTrue();
        assertThat(NotificationType.SEVEN_DAYS_BEFORE.getDaysBeforeDeadline()).isEqualTo(7);
        assertThat(NotificationType.TWO_DAYS_BEFORE.isDeadlineReminder()).isTrue();
        assertThat(NotificationType.TWO_DAYS_BEFORE.getDaysBeforeDeadline()).isEqualTo(2);
    }

    @Test
    void alertRelatedTypes_haveNoDayCount() {
        for (NotificationType type : new NotificationType[]{
                NotificationType.AT_RISK,
                NotificationType.OVERDUE,
                NotificationType.ALERT_ACKNOWLEDGED,
                NotificationType.DEADLINE_UPDATED}) {
            assertThat(type.isDeadlineReminder()).as(type.name()).isFalse();
            assertThat(type.getDaysBeforeDeadline()).as(type.name()).isNull();
        }
    }
}
