package edu.cit.dasig_core.features.committee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateCommitteeRequest {

    @NotBlank(message = "Committee name is required")
    @Size(max = 255, message = "Committee name must not exceed 255 characters")
    private String name;

    private String description;

    private List<Long> organizationIds;

    private List<Long> committeeLeadIds;

}
