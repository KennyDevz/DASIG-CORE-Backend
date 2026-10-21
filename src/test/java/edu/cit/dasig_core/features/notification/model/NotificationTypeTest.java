package edu.cit.dasig_core.features.notification.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeTest {

    @Test
    void onlyAlertAndDeadlineUpdateTypesExist() {
        assertThat(NotificationType.values()).containsExactly(
                NotificationType.AT_RISK,
                NotificationType.OVERDUE,
                NotificationType.ALERT_ACKNOWLEDGED,
                NotificationType.DEADLINE_UPDATED);
    }
}
