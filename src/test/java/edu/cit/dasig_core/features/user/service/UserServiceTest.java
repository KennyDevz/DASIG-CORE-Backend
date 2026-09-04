package edu.cit.dasig_core.features.user.service;

import edu.cit.dasig_core.core.event.UserCreatedEvent;
import edu.cit.dasig_core.features.user.dto.ChangePasswordRequest;
import edu.cit.dasig_core.features.user.dto.CreateUserRequest;
import edu.cit.dasig_core.features.user.dto.UpdateUserRequest;
import edu.cit.dasig_core.features.user.dto.UserResponse;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, eventPublisher);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User existingUser(Long id, String email, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setName("Existing User");
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole("STAFF");
        user.setOrganizationId(1L);
        user.setStatus("Active");
        return user;
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of())
        );
    }

    // ---- registerUser ----

    @Test
    void registerUser_throwsWhenEmailAlreadyInUse() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Jane");
        request.setEmail("jane@example.com");
        request.setRole("STAFF");
        request.setOrganizationId(1L);

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is already in use.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_throwsWhenStaffHasNoOrganization() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Jane");
        request.setEmail("jane@example.com");
        request.setRole("STAFF");
        request.setOrganizationId(null);

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization ID is required for TBI Managers and Staff.");
    }

    @Test
    void registerUser_throwsWhenAdminHasOrganization() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Admin");
        request.setEmail("admin@example.com");
        request.setRole("DASIG_ADMIN");
        request.setOrganizationId(5L);

        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DASIG Admin should not be tied to a specific organization.");
    }

    @Test
    void registerUser_savesHashedPasswordAndPublishesEventWithPlainTextPassword() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Jane");
        request.setEmail("jane@example.com");
        request.setRole("STAFF");
        request.setOrganizationId(1L);

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        UserResponse response = userService.registerUser(request);

        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getId()).isEqualTo(10L);

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());
        assertThat(savedUserCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUserCaptor.getValue().isMustChangePassword()).isTrue();

        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(eventCaptor.getValue().getPlainTextPassword()).isNotBlank();
        // The plain-text password handed to the email event must NOT be the hash we stored
        assertThat(eventCaptor.getValue().getPlainTextPassword()).isNotEqualTo("hashed-password");
    }

    // ---- modifyUser ----

    @Test
    void modifyUser_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("X");
        request.setEmail("x@example.com");
        request.setRole("STAFF");
        request.setOrganizationId(1L);

        assertThatThrownBy(() -> userService.modifyUser(99L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found with ID: 99");
    }

    @Test
    void modifyUser_throwsWhenEmailTakenByAnotherUser() {
        User user = existingUser(1L, "old@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("new@example.com", 1L)).thenReturn(true);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("X");
        request.setEmail("new@example.com");
        request.setRole("STAFF");
        request.setOrganizationId(1L);

        assertThatThrownBy(() -> userService.modifyUser(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is already in use by another account.");
    }

    @Test
    void modifyUser_updatesFieldsOnSuccess() {
        User user = existingUser(1L, "old@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("new@example.com", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("New Name");
        request.setEmail("new@example.com");
        request.setRole("TBI_MANAGER");
        request.setOrganizationId(2L);

        UserResponse response = userService.modifyUser(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getRole()).isEqualTo("TBI_MANAGER");
        assertThat(response.getOrganizationId()).isEqualTo(2L);
    }

    // ---- deactivateAccount ----

    @Test
    void deactivateAccount_throwsWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateAccount(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found with ID: 1");
    }

    @Test
    void deactivateAccount_setsStatusInactive() {
        User user = existingUser(1L, "user@example.com", "hash");
        user.setStatus("Active");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.deactivateAccount(1L);

        assertThat(user.getStatus()).isEqualTo("Inactive");
        verify(userRepository).save(user);
    }

    // ---- changeOwnPassword ----

    @Test
    void changeOwnPassword_throwsWhenNotAuthenticated() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("temp123");
        request.setNewPassword("newpass123");

        assertThatThrownBy(() -> userService.changeOwnPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authentication is required.");
    }

    @Test
    void changeOwnPassword_throwsWhenCurrentPasswordIncorrect() {
        User user = existingUser(1L, "user@example.com", "hashed-temp");
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-temp")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("newpass123");

        assertThatThrownBy(() -> userService.changeOwnPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current password is incorrect.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changeOwnPassword_throwsWhenNewPasswordSameAsCurrent() {
        User user = existingUser(1L, "user@example.com", "hashed-temp");
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("temp123", "hashed-temp")).thenReturn(true);
        // The new password check re-uses the same encoder.matches call against the current hash
        when(passwordEncoder.matches("temp123", "hashed-temp")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("temp123");
        request.setNewPassword("temp123");

        assertThatThrownBy(() -> userService.changeOwnPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New password must be different from your current password.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changeOwnPassword_updatesHashAndClearsMustChangePasswordFlagOnSuccess() {
        User user = existingUser(1L, "user@example.com", "hashed-temp");
        user.setMustChangePassword(true);
        authenticateAs("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("temp123", "hashed-temp")).thenReturn(true);
        when(passwordEncoder.matches(eq("newSecurePass1"), anyString())).thenReturn(false);
        when(passwordEncoder.encode("newSecurePass1")).thenReturn("hashed-new");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("temp123");
        request.setNewPassword("newSecurePass1");

        userService.changeOwnPassword(request);

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new");
        assertThat(user.isMustChangePassword()).isFalse();
        verify(userRepository).save(user);
    }

    // ---- getAllUsers / getUserById ----

    @Test
    void getUserById_throwsWhenNotFound() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found with ID: 42");
    }

    @Test
    void getAllUsers_mapsEveryUserToResponse() {
        User a = existingUser(1L, "a@example.com", "hash-a");
        User b = existingUser(2L, "b@example.com", "hash-b");
        when(userRepository.findAll()).thenReturn(List.of(a, b));

        List<UserResponse> responses = userService.getAllUsers();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(UserResponse::getEmail)
                .containsExactlyInAnyOrder("a@example.com", "b@example.com");
    }
}
