package edu.cit.dasig_core.features.kpisubmission.repository;

import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KpiSubmissionRepository extends JpaRepository<KpiSubmission, Long> {

    List<KpiSubmission> findByKpiDefinitionId(Long kpiDefinitionId);

    List<KpiSubmission> findByKpiDefinitionOrganizationId(Long organizationId);

    List<KpiSubmission> findByKpiDefinitionOrganizationIdOrderByDateCreatedDesc(Long organizationId);

    boolean existsByKpiDefinitionIdAndReportingPeriodAndSubmissionType(
            Long kpiDefinitionId,
            String reportingPeriod,
            SubmissionType submissionType
    );

    Optional<KpiSubmission> findByKpiDefinitionIdAndReportingPeriodAndSubmissionType(
            Long kpiDefinitionId,
            String reportingPeriod,
            SubmissionType submissionType
    );

    Optional<KpiSubmission> findFirstByKpiDefinitionIdOrderByDateCreatedDesc(Long kpiDefinitionId);

    Optional<KpiSubmission> findFirstByKpiDefinitionIdAndSubmissionTypeOrderByDateCreatedDesc(
            Long kpiDefinitionId,
            SubmissionType submissionType
    );
}
