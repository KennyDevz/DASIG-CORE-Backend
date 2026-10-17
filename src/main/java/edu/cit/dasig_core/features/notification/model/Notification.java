package edu.cit.dasig_core.features.notification.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_kpi_org_dedupe",
                columnNames = {"kpi_definition_id", "organization_id", "dedupe_key"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    public static final String STATUS_UNREAD = "UNREAD";
    public static final String STATUS_READ = "READ";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kpi_definition_id", nullable = false)
    private Long kpiDefinitionId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(nullable = false)
    private String status;

    /** The alert this notification is about (At Risk, Overdue, Alert Acknowledged); null for deadline reminders. */
    @Column(name = "alert_id")
    private Long alertId;

    /**
     * Prevents duplicate notifications per KPI and organization. Built per type, e.g.
     * "SEVEN_DAYS_BEFORE:2026-05-01" (a new deadline allows new reminders) or "AT_RISK:alert-42".
     * See {@link NotificationKeys}.
     */
    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
