package edu.cit.dasig_core.core.event;

import java.time.LocalDate;

/**
 * Published when a KPI event should be emailed to the committee (UC-2.1, UC-2.2, UC-4.5).
 * The dates and remark are only used for {@link KpiEmailType#DEADLINE_UPDATED}.
 */
public record KpiEmailEvent(
        KpiEmailType type,
        Long kpiDefinitionId,
        LocalDate oldDeadline,
        LocalDate newDeadline,
        String adminRemark
) {

    public static KpiEmailEvent assigned(Long kpiDefinitionId) {
        return new KpiEmailEvent(KpiEmailType.KPI_ASSIGNED, kpiDefinitionId, null, null, null);
    }

    public static KpiEmailEvent deadlineUpdated(Long kpiDefinitionId, LocalDate oldDeadline, LocalDate newDeadline, String adminRemark) {
        return new KpiEmailEvent(KpiEmailType.DEADLINE_UPDATED, kpiDefinitionId, oldDeadline, newDeadline, adminRemark);
    }
}
