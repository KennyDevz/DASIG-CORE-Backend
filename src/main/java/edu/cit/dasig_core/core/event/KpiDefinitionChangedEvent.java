package edu.cit.dasig_core.core.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class KpiDefinitionChangedEvent {

    public enum ChangeType {
        CREATED,
        UPDATED,
        DELETED
    }

    private final Long kpiDefinitionId;
    private final Long organizationId;
    private final ChangeType changeType;
}