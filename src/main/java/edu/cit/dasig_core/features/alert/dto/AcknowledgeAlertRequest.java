package edu.cit.dasig_core.features.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcknowledgeAlertRequest {

    @NotBlank(message = "A remark is required to acknowledge an alert")
    @Size(max = 500, message = "Remark must be 500 characters or fewer")
    private String remark;
}
