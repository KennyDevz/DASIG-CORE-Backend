package edu.cit.dasig_core.features.alert.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * The Committee Lead's written reason why an overdue KPI was not accomplished (UC-4.4).
 * Exactly one explanation can exist per Overdue alert. The alert's explanation status
 * ("Awaiting explanation" / "Explained") is derived from whether this row exists.
 */
@Entity
@Table(name = "overdue_explanations")
@Getter
@Setter
@NoArgsConstructor
public class OverdueExplanation {

    public static final int MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false, unique = true)
    private Long alertId;

    @Column(name = "kpi_definition_id", nullable = false)
    private Long kpiDefinitionId;

    @Column(nullable = false, length = MAX_LENGTH)
    private String explanation;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_role", nullable = false, length = 50)
    private String authorRole;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
