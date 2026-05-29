package edu.cit.dasig_core.features.report.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GenericGenerator(
            name = "custom_report_id",
            strategy = "edu.cit.dasig_core.features.report.config.CustomIdGenerator" // 2. Tells Hibernate where your Java generator is
    )
    @GeneratedValue(generator = "custom_report_id")
    @Column(name = "id", length = 20) // 3. Changed from Long to String to accept text like "PR-2026-0001"
    private String id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "narrative_text", columnDefinition = "TEXT", nullable = false)
    private String narrativeText;

    @Column(nullable = false)
    private String status; // GENERATED, FAILED

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;
}