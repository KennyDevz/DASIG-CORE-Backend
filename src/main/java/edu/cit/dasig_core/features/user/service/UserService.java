package edu.cit.dasig_core.features.user.service;

import edu.cit.dasig_core.core.event.UserCreatedEvent;
import edu.cit.dasig_core.core.smtp.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.dasig_core.features.user.dto.CreateUserRequest;
import edu.cit.dasig_core.features.user.dto.UpdateUserRequest;
import edu.cit.dasig_core.features.user.dto.UserResponse;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserResponse registerUser(CreateUserRequest request) {
        // 1. Check for duplicate emails
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use.");
        }

        // 2. Validate role-based organization rules
        validateOrganizationRequirement(request.getRole(), request.getOrganizationId());

        // 3. Map DTO to Entity
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setOrganizationId(request.getOrganizationId());
        user.setStatus("Active");

        // 4. SRS UC-1.1.2: System auto-generates a temporary password & hashes it
        String tempPassword = UUID.randomUUID().toString().substring(0, 10);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));

        // 5. Save to database
        User savedUser = userRepository.save(user);

        eventPublisher.publishEvent(new UserCreatedEvent(savedUser.getEmail(), savedUser.getName(), tempPassword));
        return mapToResponse(savedUser);
    }

    @Transactional
    public UserResponse modifyUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));

        // Ensure the new email isn't taken by someone else
        if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new IllegalArgumentException("Email is already in use by another account.");
        }

        validateOrganizationRequirement(request.getRole(), request.getOrganizationId());

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setOrganizationId(request.getOrganizationId());

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    @Transactional
    public void deactivateAccount(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        
        // SRS UC-1.1.4: Soft delete by flagging account as inactive
        user.setStatus("Inactive");
        userRepository.save(user);
    }

    /**
     * Cross-field validation: Ensures TBI Managers/Staff belong to an organization, 
     * but prevents the global DASIG Admin from being restricted to one.
     */
    private void validateOrganizationRequirement(String role, Long organizationId) {
        if (("TBI_MANAGER".equals(role) || "STAFF".equals(role)) && organizationId == null) {
            throw new IllegalArgumentException("Organization ID is required for TBI Managers and Staff.");
        }
        if ("DASIG_ADMIN".equals(role) && organizationId != null) {
            throw new IllegalArgumentException("DASIG Admin should not be tied to a specific organization.");
        }
    }

    /**
     * Converts the database Entity into a secure Response DTO (stripping the password)
     */
    private UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setOrganizationId(user.getOrganizationId());
        return response;
    }
}
