package edu.cit.dasig_core.features.committee.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateCommitteeRequest {

    @NotBlank(message = "Committee name is required")
    private String name;

    private String description;

    private List<Long> organizationIds;

}
