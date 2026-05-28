package edu.cit.dasig_core.core.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class KpiSubmittedEvent {
    private final Long submissionId;
    private final BigDecimal submittedValue;
}
