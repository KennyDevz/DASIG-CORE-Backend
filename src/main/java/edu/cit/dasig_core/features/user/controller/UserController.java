package edu.cit.dasig_core.features.user.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import edu.cit.dasig_core.features.user.dto.CreateUserRequest;
import edu.cit.dasig_core.features.user.dto.UpdateUserRequest;
import edu.cit.dasig_core.features.user.dto.UserResponse;
import edu.cit.dasig_core.features.user.service.UserService;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('DASIG_ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.registerUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id, 
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.modifyUser(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateAccount(id);
        return ResponseEntity.noContent().build();
    }
}