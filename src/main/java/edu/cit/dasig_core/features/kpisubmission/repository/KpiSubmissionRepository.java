package edu.cit.dasig_core.features.kpisubmission.repository;

import edu.cit.dasig_core.features.kpisubmission.model.KpiSubmission;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<KpiSubmission> findByKpiDefinitionIdAndOrganizationIdInAndSubmissionType(
            Long kpiDefinitionId,
            List<Long> organizationIds,
            SubmissionType submissionType
    );

    List<KpiSubmission> findByKpiDefinitionIdAndOrganizationIdIn(
            Long kpiDefinitionId,
            List<Long> organizationIds
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

    @Query("SELECT s FROM KpiSubmission s WHERE s.kpiDefinition.committee.id = :committeeId ORDER BY s.dateCreated DESC")
    List<KpiSubmission> findByCommitteeIdOrderByDateCreatedDesc(@Param("committeeId") Long committeeId);

    @Query("SELECT s FROM KpiSubmission s WHERE s.kpiDefinition.committee.id IN :committeeIds ORDER BY s.dateCreated DESC")
    List<KpiSubmission> findByCommitteeIdsOrderByDateCreatedDesc(@Param("committeeIds") List<Long> committeeIds);

    @Query("SELECT COUNT(s) FROM KpiSubmission s WHERE s.kpiDefinition.committee.id IN :committeeIds AND s.reviewStatus = :reviewStatus AND s.submissionType = :submissionType")
    long countByCommitteeIdsAndReviewStatusAndSubmissionType(
            @Param("committeeIds") List<Long> committeeIds,
            @Param("reviewStatus") SubmissionReviewStatus reviewStatus,
            @Param("submissionType") SubmissionType submissionType
    );

    @Query("SELECT COUNT(s) FROM KpiSubmission s WHERE s.submittedBy.id = :userId AND s.submissionType = :submissionType AND s.reviewStatus IN :reviewStatuses AND s.memberViewed = false")
    long countUnviewedReviewedSubmissions(
            @Param("userId") Long userId,
            @Param("submissionType") SubmissionType submissionType,
            @Param("reviewStatuses") List<SubmissionReviewStatus> reviewStatuses
    );

    List<KpiSubmission> findBySubmittedByIdAndSubmissionTypeAndReviewStatusInAndMemberViewedFalse(
            Long userId,
            SubmissionType submissionType,
            List<SubmissionReviewStatus> reviewStatuses
    );

    long countBySubmittedByIdAndReviewStatusInAndMemberViewedFalse(
            Long userId,
            List<SubmissionReviewStatus> reviewStatuses
    );

    List<KpiSubmission> findBySubmittedByIdAndReviewStatusInAndMemberViewedFalse(
            Long userId,
            List<SubmissionReviewStatus> reviewStatuses
    );

    @Query("SELECT s.kpiDefinition.committee.id, COUNT(s) FROM KpiSubmission s WHERE s.kpiDefinition.committee.id IN :committeeIds AND s.reviewStatus = edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus.PENDING GROUP BY s.kpiDefinition.committee.id")
    List<Object[]> countPendingSubmissionsByCommitteeIds(@Param("committeeIds") List<Long> committeeIds);
}
