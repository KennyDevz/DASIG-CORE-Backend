package edu.cit.dasig_core.features.alert.repository;

import edu.cit.dasig_core.features.alert.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    void deleteByKpiDefinitionId(Long kpiDefinitionId);

    void deleteBySubmissionId(Long submissionId);

    /** Open (or otherwise filtered) alerts of one type for a KPI. */
    List<Alert> findByKpiDefinitionIdAndAlertTypeAndStatusIn(Long kpiDefinitionId, String alertType, Collection<String> statuses);

    /** All alerts for a KPI in the given statuses, e.g. every open alert. */
    List<Alert> findByKpiDefinitionIdAndStatusIn(Long kpiDefinitionId, Collection<String> statuses);

    /** Alerts in one status, newest first (used by the Alerts page status filter). */
    List<Alert> findByStatusOrderByDetectedAtDesc(String status);
}