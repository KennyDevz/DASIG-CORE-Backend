package edu.cit.dasig_core.features.kpisubmission.model;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "kpi_submissions")
@Getter
@Setter
@NoArgsConstructor
public class KpiSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_definition_id", nullable = false)
    private KpiDefinition kpiDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private User submittedBy;

    @Column(name = "submitted_value", nullable = false)
    private Double submittedValue;

    @Column(name = "reporting_period", nullable = false)
    private String reportingPeriod;

    @Column(name = "submission_date", nullable = false)
    private LocalDate submissionDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false)
    private SubmissionType submissionType;

    @Column(name = "achievement_rate", nullable = false)
    private Double achievementRate;

    @Column(name = "performance_status", nullable = false)
    private String performanceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status")
    private SubmissionReviewStatus reviewStatus = SubmissionReviewStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_submission_id")
    private KpiSubmission sourceSubmission;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime dateCreated;
}
