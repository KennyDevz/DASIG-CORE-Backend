package edu.cit.dasig_core.features.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(DASIG_ADMIN|TBI_MANAGER|STAFF)$", message = "Role must be DASIG_ADMIN, TBI_MANAGER, or STAFF")
    private String role;

    private Long organizationId;
}