package edu.cit.dasig_core.features.committee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.cit.dasig_core.features.committee.model.Committee;

@Repository
public interface CommitteeRepository extends JpaRepository<Committee, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

}
