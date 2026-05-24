package edu.cit.dasig_core.alert;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class KpiSubmittedEvent {
    private final Long submission;
    private final BigDecimal value;
}
