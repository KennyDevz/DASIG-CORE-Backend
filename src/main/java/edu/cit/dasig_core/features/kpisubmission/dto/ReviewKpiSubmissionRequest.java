package edu.cit.dasig_core.features.kpisubmission.dto;

import edu.cit.dasig_core.features.kpisubmission.model.SubmissionReviewStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewKpiSubmissionRequest {
    @NotNull
    private SubmissionReviewStatus reviewStatus;

    private String rejectionReason;
}
