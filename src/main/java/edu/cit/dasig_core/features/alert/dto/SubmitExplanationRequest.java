package edu.cit.dasig_core.features.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitExplanationRequest {

    @NotBlank(message = "An explanation is required")
    @Size(max = 2000, message = "Explanation must be 2,000 characters or fewer")
    private String explanation;
}
