package edu.cit.dasig_core.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Size(max = 255, message = "Username must not exceed 255 characters")
    private String username;

    @NotBlank
    @Size(max = 100, message = "Password must not exceed 100 characters")
    private String password;
}
