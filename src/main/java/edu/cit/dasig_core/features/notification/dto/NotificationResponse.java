package edu.cit.dasig_core.features.notification.dto;

import edu.cit.dasig_core.features.notification.model.NotificationType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long id;
    private Long kpiDefinitionId;
    private String kpiName;
    private Long organizationId;
    private NotificationType notificationType;
    /** Only set for deadline reminders; null for alert-related notification types. */
    private LocalDate deadline;
    private String status;
    private String message;
    private LocalDateTime createdAt;
    /** The alert this notification is about (At Risk, Overdue, Alert Acknowledged); null otherwise. */
    private Long alertId;
}
