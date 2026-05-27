package edu.cit.dasig_core.features.alert.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long submissionId;
    private String status;
    private LocalDateTime dateDetected;

    @PrePersist
    protected void onCreate() {
        this.dateDetected = LocalDateTime.now();
    }
}
