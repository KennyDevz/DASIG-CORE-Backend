package edu.cit.dasig_core.features.alert.repository;

import edu.cit.dasig_core.features.alert.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByStatus(String status);

    boolean existsBySubmissionId(Long submissionId);

    List<Alert> findAllByOrderByDetectedAtDesc();

    @Query("""
            SELECT a FROM Alert a
            WHERE a.submissionId IN (
                SELECT s.id FROM KpiSubmission s
                WHERE s.kpiDefinition.organization.id = :organizationId
            )
            ORDER BY a.detectedAt DESC
            """)
    List<Alert> findByOrganizationId(@Param("organizationId") Long organizationId);
}
