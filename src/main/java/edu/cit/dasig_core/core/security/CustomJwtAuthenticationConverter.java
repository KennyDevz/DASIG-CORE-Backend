package edu.cit.dasig_core.core.security;

import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final JwtGrantedAuthoritiesConverter authoritiesConverter;

    public CustomJwtAuthenticationConverter(UserRepository userRepository, OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        this.authoritiesConverter.setAuthoritiesClaimName("roles");
        this.authoritiesConverter.setAuthorityPrefix("ROLE_");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // 1. Try to get authorities from "roles" claim (Microsoft Entra ID tokens)
        Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);

        // 2. Fallback: if no authorities from "roles", check for singular "role" claim (local JWT tokens)
        if (authorities.isEmpty()) {
            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                authorities = Collections.singleton(new org.springframework.security.core.authority.SimpleGrantedAuthority(role));
            }
        }

        // 3. Extract Email (Entra ID standardizes on preferred_username for v2 tokens)
        String email = jwt.getClaimAsString("preferred_username");
        if (email == null) {
            email = jwt.getClaimAsString("email");
        }
        if (email == null) {
            email = jwt.getClaimAsString("unique_name");
        }
        // Fallback for local tokens: subject is the email
        if (email == null) {
            email = jwt.getSubject();
        }

        // 4. JIT Provisioning & Role Synchronization (only for Microsoft tokens)
        if (email != null && jwt.getClaimAsStringList("roles") != null) {
            
            // Determine the highest privilege role from the Entra ID token
            String entraRole = "STAFF"; // Fallback default
            List<String> roles = jwt.getClaimAsStringList("roles");
            
            if (roles != null && !roles.isEmpty()) {
                if (roles.contains("DASIG-ADMIN")) {
                    entraRole = "DASIG_ADMIN";
                } else if (roles.contains("DASIG-COMMITTEE-LEAD")) {
                    entraRole = "DASIG_COMMITTEE_LEAD";
                } else if (roles.contains("DASIG-MEMBER")) {
                    entraRole = "DASIG_MEMBER";
                } else {
                    entraRole = roles.get(0).replace("-", "_");
                }
            }

            Optional<User> existingUserOpt = userRepository.findByEmail(email);

            if (existingUserOpt.isEmpty()) {
                // --- SCENARIO A: NEW USER (JIT PROVISIONING) ---
                User newUser = new User();
                newUser.setEmail(email);
                
                String name = jwt.getClaimAsString("name");
                newUser.setName(name != null ? name : email);
                newUser.setRole(entraRole);

                // Domain-Based Organization Routing (Only runs once on creation)
                if ("DASIG_ADMIN".equals(entraRole)) {
                    newUser.setOrganizationId(1L); 
                } else {
                    String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
                    Optional<Organization> org = organizationRepository.findByContactEmailEndingWith("@" + domain);
                    newUser.setOrganizationId(org.isPresent() ? org.get().getId() : 2L);
                }

                newUser.setStatus("Active");
                newUser.setMustChangePassword(false);
                newUser.setPasswordHash("");
                
                userRepository.save(newUser);
                
            } else {
                // --- SCENARIO B: EXISTING USER (ROLE SYNCHRONIZATION) ---
                User existingUser = existingUserOpt.get();
                
                // Only trigger a database update if their Entra ID role is different from their database role
                if (!entraRole.equals(existingUser.getRole())) {
                    existingUser.setRole(entraRole);
                    userRepository.save(existingUser);
                }
            }
        }

        // 5. Return the token using the EMAIL as the Principal Name instead of the Entra UUID
        return new JwtAuthenticationToken(jwt, authorities, email);
    }
}