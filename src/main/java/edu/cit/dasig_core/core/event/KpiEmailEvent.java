package edu.cit.dasig_core.core.event;

import java.time.LocalDate;

/**
 * Published when a KPI event should be emailed (UC-2.1, UC-2.2, UC-3.1, UC-3.3, UC-4.5).
 * KPI events carry the KPI id (and the dates and remark for {@link KpiEmailType#DEADLINE_UPDATED});
 * submission events carry the submission id.
 */
public record KpiEmailEvent(
        KpiEmailType type,
        Long kpiDefinitionId,
        LocalDate oldDeadline,
        LocalDate newDeadline,
        String adminRemark,
        Long submissionId
) {

    public static KpiEmailEvent assigned(Long kpiDefinitionId) {
        return new KpiEmailEvent(KpiEmailType.KPI_ASSIGNED, kpiDefinitionId, null, null, null, null);
    }

    public static KpiEmailEvent deadlineUpdated(Long kpiDefinitionId, LocalDate oldDeadline, LocalDate newDeadline, String adminRemark) {
        return new KpiEmailEvent(KpiEmailType.DEADLINE_UPDATED, kpiDefinitionId, oldDeadline, newDeadline, adminRemark, null);
    }

    public static KpiEmailEvent submissionSent(Long submissionId) {
        return new KpiEmailEvent(KpiEmailType.SUBMISSION_SENT, null, null, null, null, submissionId);
    }

    public static KpiEmailEvent submissionReviewed(Long submissionId) {
        return new KpiEmailEvent(KpiEmailType.SUBMISSION_REVIEWED, null, null, null, null, submissionId);
    }

    public static KpiEmailEvent finalValueApproved(Long submissionId) {
        return new KpiEmailEvent(KpiEmailType.FINAL_VALUE_APPROVED, null, null, null, null, submissionId);
    }
}
