package edu.cit.dasig_core.features.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.cit.dasig_core.features.user.model.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Checks if an email is already taken during new user registration
    boolean existsByEmail(String email);
    
    // Checks if an email is already taken by a DIFFERENT user during an update
    boolean existsByEmailAndIdNot(String email, Long id);
    
    // Used by our AdminSeederConfig to check if the primary DASIG_ADMIN already exists
    Optional<User> findByRole(String role);

    Optional<User> findByEmail(String email);

    // Used to find active users by role for email dispatch (e.g., KPI alert notifications)
    List<User> findByRoleAndStatus(String role, String status);
}
