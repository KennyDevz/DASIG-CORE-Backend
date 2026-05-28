package edu.cit.dasig_core.features.kpisubmission.repository;

import edu.cit.dasig_core.features.kpi.model.KpiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KpiAssignmentRepository extends JpaRepository<KpiDefinition, Long> {

    Optional<KpiDefinition> findByIdAndOrganizationId(Long id, Long organizationId);

    List<KpiDefinition> findByOrganizationId(Long organizationId);
}
