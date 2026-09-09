package edu.cit.dasig_core.features.kpisubmission.dto;

public record KpiSubmissionBadgeCountsResponse(
        long pendingCount,
        long unreadReviewCount
) {}
