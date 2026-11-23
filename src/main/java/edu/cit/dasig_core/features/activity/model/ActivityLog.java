package edu.cit.dasig_core.features.activity.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One recorded user action during the final user validation (who, what, when).
 * Only written when app.activity-log=true.
 */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 255)
    private String path;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ActivityLog(String userEmail, String action, String path) {
        this.userEmail = userEmail;
        this.action = action;
        this.path = path;
    }
}
