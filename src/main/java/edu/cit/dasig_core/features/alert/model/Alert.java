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

    public static final String TYPE_OVERDUE = "OVERDUE";
    public static final String TYPE_AT_RISK = "AT_RISK";

    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_WARNING = "WARNING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kpi_definition_id")
    private Long kpiDefinitionId;

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
}