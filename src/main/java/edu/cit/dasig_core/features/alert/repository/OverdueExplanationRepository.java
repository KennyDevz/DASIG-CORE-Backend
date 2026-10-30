package edu.cit.dasig_core.features.alert.repository;

import edu.cit.dasig_core.features.alert.model.OverdueExplanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OverdueExplanationRepository extends JpaRepository<OverdueExplanation, Long> {

    Optional<OverdueExplanation> findByAlertId(Long alertId);

    boolean existsByAlertId(Long alertId);

    List<OverdueExplanation> findByAlertIdIn(Collection<Long> alertIds);

    List<OverdueExplanation> findByKpiDefinitionIdOrderByCreatedAtDesc(Long kpiDefinitionId);

    void deleteByKpiDefinitionId(Long kpiDefinitionId);
}
