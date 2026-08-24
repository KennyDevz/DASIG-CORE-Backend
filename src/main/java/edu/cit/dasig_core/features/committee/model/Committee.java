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

@Entity
@Table(name = "committees")
@Getter
@Setter
@NoArgsConstructor
public class Committee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "Active";

    @OneToMany(mappedBy = "committee", cascade = CascadeType.ALL)
    private List<Organization> organizations = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime dateCreated;
}
