package edu.cit.dasig_core.features.kpi.repository;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KpiDefinitionRepository extends JpaRepository<KpiDefinition, Long> {
    List<KpiDefinition> findByOrganizationId(Long organizationId);
}