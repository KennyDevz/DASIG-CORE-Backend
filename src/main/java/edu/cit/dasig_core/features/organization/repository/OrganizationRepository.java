package edu.cit.dasig_core.features.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.cit.dasig_core.features.organization.model.Organization;

import java.util.List;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

}