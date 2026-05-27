package edu.cit.dasig_core.features.kpi.model;

import edu.cit.dasig_core.features.organization.model.Organization;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "kpi_definitions")
@Getter
@Setter
@NoArgsConstructor
public class KpiDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "target_value", nullable = false)
    private Double targetValue;

    @Column(nullable = false)
    private String unit; // e.g., Count, Percentage, Currency

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(nullable = false)
    private Double threshold; // Numeric value triggering an alert

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
}