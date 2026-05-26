package edu.cit.dasig_core.features.organization.dto;

import lombok.Data;

@Data
public class OrganizationResponse {

    private Long id;
    private String name;
    private String description;
    private String address;
    private String contactEmail;
    private String contactNumber;
    private String status;

}
