package edu.cit.dasig_core.features.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.cit.dasig_core.features.user.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Checks if an email is already taken during new user registration
    boolean existsByEmail(String email);
    
    // Checks if an email is already taken by a DIFFERENT user during an update
    boolean existsByEmailAndIdNot(String email, Long id);
    
    // Used by our AdminSeederConfig to check if the primary DASIG_ADMIN already exists
    Optional<User> findByRole(String role);
}