package edu.cit.dasig_core.features.notification.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationKeysTest {

    @Test
    void forAlert_isUniquePerAlertAndType() {
        assertThat(NotificationKeys.forAlert(NotificationType.AT_RISK, 42L)).isEqualTo("AT_RISK:alert-42");
        assertThat(NotificationKeys.forAlert(NotificationType.AT_RISK, 43L))
                .isNotEqualTo(NotificationKeys.forAlert(NotificationType.AT_RISK, 42L));
        assertThat(NotificationKeys.forAlert(NotificationType.ALERT_ACKNOWLEDGED, 42L))
                .isNotEqualTo(NotificationKeys.forAlert(NotificationType.AT_RISK, 42L));
    }

    @Test
    void deadlineUpdated_includesOldAndNewDeadline() {
        String key = NotificationKeys.deadlineUpdated(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15));

        assertThat(key).isEqualTo("DEADLINE_UPDATED:2026-05-01->2026-06-15");
    }

    @Test
    void keysFitInDedupeKeyColumn() {
        String longest = NotificationKeys.forAlert(NotificationType.ALERT_ACKNOWLEDGED, Long.MAX_VALUE);

        assertThat(longest.length()).isLessThanOrEqualTo(120);
    }
}
