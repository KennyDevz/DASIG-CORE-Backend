package edu.cit.dasig_core.features.committee.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.user.model.User;

@Entity
@Table(name = "committees")
@Getter
@Setter
@NoArgsConstructor
public class Committee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "Active";

    @ManyToMany
    @JoinTable(
            name = "committees_organizations",
            joinColumns = @JoinColumn(name = "committee_id"),
            inverseJoinColumns = @JoinColumn(name = "organization_id")
    )
    private List<Organization> organizations = new ArrayList<>();

    @ManyToMany(mappedBy = "committees")
    private List<User> users = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime dateCreated;
}
