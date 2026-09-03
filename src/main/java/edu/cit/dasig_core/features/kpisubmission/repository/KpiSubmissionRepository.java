package edu.cit.dasig_core.features.kpisubmission.repository;

import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KpiSubmissionRepository extends JpaRepository<KpiSubmission, Long> {

    List<KpiSubmission> findByKpiDefinitionId(Long kpiDefinitionId);

    List<KpiSubmission> findByKpiDefinitionIdAndSubmissionType(Long kpiDefinitionId, SubmissionType submissionType);

    List<KpiSubmission> findByKpiDefinitionIdAndOrganizationId(Long kpiDefinitionId, Long organizationId);

    List<KpiSubmission> findByKpiDefinitionIdAndOrganizationIdAndSubmissionType(
            Long kpiDefinitionId,
            Long organizationId,
            SubmissionType submissionType
    );

    List<KpiSubmission> findByOrganizationId(Long organizationId);

    List<KpiSubmission> findByOrganizationIdIn(List<Long> organizationIds);

    List<KpiSubmission> findByOrganizationIdOrderByDateCreatedDesc(Long organizationId);

    List<KpiSubmission> findByOrganizationIdAndReviewStatusOrderByDateCreatedDesc(
            Long organizationId,
            SubmissionReviewStatus reviewStatus
    );

    List<KpiSubmission> findByKpiDefinitionIdAndOrganizationIdAndReportingPeriodAndSubmissionType(
            Long kpiDefinitionId,
            Long organizationId,
            String reportingPeriod,
            SubmissionType submissionType
    );

    boolean existsBySourceSubmissionId(Long sourceSubmissionId);
}
