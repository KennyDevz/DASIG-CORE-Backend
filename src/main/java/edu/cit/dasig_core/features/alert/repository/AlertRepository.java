package edu.cit.dasig_core.features.alert.repository;

import edu.cit.dasig_core.features.alert.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByStatus(String status);

    boolean existsBySubmissionId(Long submissionId);

    boolean existsByKpiDefinitionIdAndAlertType(Long kpiDefinitionId, String alertType);

    boolean existsByKpiDefinitionIdAndAlertTypeAndStatus(Long kpiDefinitionId, String alertType, String status);

    List<Alert> findByKpiDefinitionIdAndAlertType(Long kpiDefinitionId, String alertType);

    List<Alert> findAllByOrderByDetectedAtDesc();

    List<Alert> findByKpiDefinitionId(Long kpiDefinitionId);
}