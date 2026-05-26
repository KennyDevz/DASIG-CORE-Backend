package edu.cit.dasig_core.features.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role; // Values: "DASIG_ADMIN", "TBI_MANAGER", "STAFF"

    @Column(nullable = false)
    private String status = "Active"; // Default status

    @Column(name = "organization_id")
    private Long organizationId; // Nullable: Only required for TBI_MANAGER and STAFF
}