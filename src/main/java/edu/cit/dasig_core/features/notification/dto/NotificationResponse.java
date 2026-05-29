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
    private int daysBeforeDeadline;
    private LocalDate deadline;
    private String status;
    private String message;
    private LocalDateTime createdAt;
}
