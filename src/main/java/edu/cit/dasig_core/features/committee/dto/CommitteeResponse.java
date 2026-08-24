package edu.cit.dasig_core.features.committee.dto;

import lombok.Data;

import java.util.List;

@Data
public class CommitteeResponse {

    private Long id;
    private String name;
    private String description;
    private String status;
    private List<Long> organizationIds;

}
