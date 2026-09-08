package edu.cit.dasig_core.features.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserResponse {
    
    private Long id;
    private String name;
    private String email;
    private String role;
    private String status;
    private Long organizationId;
    private List<Long> committeeIds;
    
}