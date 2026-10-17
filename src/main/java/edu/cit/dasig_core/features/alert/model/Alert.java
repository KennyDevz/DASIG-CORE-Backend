package edu.cit.dasig_core.features.alert.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    public static final String STATUS_UNACKNOWLEDGED = "UNACKNOWLEDGED";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATUS_RESOLVED = "RESOLVED";

    public static final String TYPE_OVERDUE = "OVERDUE";
    public static final String TYPE_AT_RISK = "AT_RISK";

    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_WARNING = "WARNING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kpi_definition_id")
    private Long kpiDefinitionId;

    /**
     * Legacy link to the submission that triggered the alert.
     * Alerts are now evaluated per KPI, so new alerts leave this null.
     */
    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "alert_type")
    private String alertType = TYPE_OVERDUE;

    @Column(name = "severity")
    private String severity = SEVERITY_CRITICAL;

    private String status = STATUS_UNACKNOWLEDGED;

    @CreationTimestamp
    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    /** When the alert was automatically resolved (target met, recovered, escalated, or deadline changed). */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** Human-readable reason the alert was resolved, e.g. "Target met" or "Progress recovered". */
    @Column(name = "resolution_reason", length = 255)
    private String resolutionReason;

    /** Remark recorded by the DASIG Admin when acknowledging the alert (max 500 characters). */
    @Column(name = "admin_remark", length = 500)
    private String adminRemark;

    /** User ID of the Admin who acknowledged the alert. */
    @Column(name = "remarked_by_id")
    private Long remarkedById;

    /** When the Admin acknowledged the alert with a remark. */
    @Column(name = "remarked_at")
    private LocalDateTime remarkedAt;

    /** An alert is open until it is resolved (Unacknowledged or Acknowledged). */
    public boolean isOpen() {
        return !STATUS_RESOLVED.equals(status);
    }

    public boolean isResolved() {
        return STATUS_RESOLVED.equals(status);
    }
}