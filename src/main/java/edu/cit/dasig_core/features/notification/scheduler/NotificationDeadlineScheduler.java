package edu.cit.dasig_core.features.notification.scheduler;

import edu.cit.dasig_core.features.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDeadlineScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDeadlineNotifications() {
        notificationService.createDeadlineNotificationsIfDue();
    }
}
