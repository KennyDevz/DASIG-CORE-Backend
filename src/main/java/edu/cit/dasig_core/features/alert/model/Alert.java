package edu.cit.dasig_core.features.alert.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long submissionId;
    private String status;

    @CreationTimestamp
    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime dateCreated;
}
