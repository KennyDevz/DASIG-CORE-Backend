package edu.cit.dasig_core.features.kpisubmission.dto;

import java.util.List;

public record MarkSubmissionsViewedRequest(
        List<Long> submissionIds
) {}
