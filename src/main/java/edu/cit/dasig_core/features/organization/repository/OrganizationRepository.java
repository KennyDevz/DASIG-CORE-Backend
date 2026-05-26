package edu.cit.dasig_core.features.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.cit.dasig_core.features.organization.model.Organization;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    // Checks if an organization name is already taken during creation
    boolean existsByName(String name);

    // Checks if a name is already taken by a DIFFERENT organization during an update
    boolean existsByNameAndIdNot(String name, Long id);

}