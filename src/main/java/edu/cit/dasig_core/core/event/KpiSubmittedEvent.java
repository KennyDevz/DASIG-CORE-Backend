package edu.cit.dasig_core.core.event;

import edu.cit.dasig_core.features.kpisubmission.model.SubmissionType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class KpiSubmittedEvent {
    private final Long submissionId;
    private final BigDecimal submittedValue;
    private final SubmissionType submissionType;
    private final Long organizationId;
}
