package edu.cit.dasig_core.core.security;

import edu.cit.dasig_core.features.organization.model.Organization;
import edu.cit.dasig_core.features.organization.repository.OrganizationRepository;
import edu.cit.dasig_core.features.user.model.User;
import edu.cit.dasig_core.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomJwtAuthenticationConverterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private CustomJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CustomJwtAuthenticationConverter(userRepository, organizationRepository);
    }

    private Jwt tokenWithRoles(String email, String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("email", email)
                .claim("roles", List.of(roles))
                .build();
    }

    @Test
    void existingAdminSigningInWithNonAdminRoleIsAssignedAnOrganization() {
        User existing = new User();
        existing.setId(1L);
        existing.setEmail("user@cit.edu");
        existing.setRole("DASIG_ADMIN");
        existing.setOrganizationId(null);

        Organization org = new Organization();
        org.setId(7L);

        when(userRepository.findByEmail("user@cit.edu")).thenReturn(Optional.of(existing));
        when(organizationRepository.findByContactEmailEndingWith("@cit.edu")).thenReturn(Optional.of(org));

        converter.convert(tokenWithRoles("user@cit.edu", "DASIG-MEMBER"));

        assertThat(existing.getRole()).isEqualTo("DASIG_MEMBER");
        assertThat(existing.getOrganizationId()).isEqualTo(7L);
        verify(userRepository).save(existing);
    }

    @Test
    void existingNonAdminWithoutOrganizationIsAssignedFallbackOrganization() {
        User existing = new User();
        existing.setId(2L);
        existing.setEmail("user@unknown.edu");
        existing.setRole("DASIG_ADMIN");
        existing.setOrganizationId(null);

        when(userRepository.findByEmail("user@unknown.edu")).thenReturn(Optional.of(existing));
        when(organizationRepository.findByContactEmailEndingWith("@unknown.edu")).thenReturn(Optional.empty());

        converter.convert(tokenWithRoles("user@unknown.edu", "DASIG-MEMBER"));

        assertThat(existing.getOrganizationId()).isEqualTo(2L);
        verify(userRepository).save(existing);
    }

    @Test
    void promotionToAdminClearsOrganization() {
        User existing = new User();
        existing.setId(3L);
        existing.setEmail("lead@cit.edu");
        existing.setRole("DASIG_MEMBER");
        existing.setOrganizationId(4L);

        when(userRepository.findByEmail("lead@cit.edu")).thenReturn(Optional.of(existing));

        converter.convert(tokenWithRoles("lead@cit.edu", "DASIG-ADMIN"));

        assertThat(existing.getRole()).isEqualTo("DASIG_ADMIN");
        assertThat(existing.getOrganizationId()).isNull();
        verify(userRepository).save(existing);
    }

    @Test
    void unchangedRoleAndMatchingOrganizationDoesNotTriggerSave() {
        User existing = new User();
        existing.setId(4L);
        existing.setEmail("user@cit.edu");
        existing.setRole("DASIG_MEMBER");
        existing.setOrganizationId(3L);

        Organization org = new Organization();
        org.setId(3L);

        when(userRepository.findByEmail("user@cit.edu")).thenReturn(Optional.of(existing));
        when(organizationRepository.findByContactEmailEndingWith("@cit.edu")).thenReturn(Optional.of(org));

        converter.convert(tokenWithRoles("user@cit.edu", "DASIG-MEMBER"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void existingUserWithStaleOrganizationIsReconciledToDomain() {
        User existing = new User();
        existing.setId(5L);
        existing.setEmail("user@cit.edu");
        existing.setRole("DASIG_MEMBER");
        existing.setOrganizationId(9L);

        Organization org = new Organization();
        org.setId(7L);

        when(userRepository.findByEmail("user@cit.edu")).thenReturn(Optional.of(existing));
        when(organizationRepository.findByContactEmailEndingWith("@cit.edu")).thenReturn(Optional.of(org));

        converter.convert(tokenWithRoles("user@cit.edu", "DASIG-MEMBER"));

        assertThat(existing.getOrganizationId()).isEqualTo(7L);
        verify(userRepository).save(existing);
    }

    @Test
    void existingAdminWithNullOrganizationRetainsNullWhileAdmin() {
        User existing = new User();
        existing.setId(6L);
        existing.setEmail("boss@cit.edu");
        existing.setRole("DASIG_ADMIN");
        existing.setOrganizationId(null);

        when(userRepository.findByEmail("boss@cit.edu")).thenReturn(Optional.of(existing));

        converter.convert(tokenWithRoles("boss@cit.edu", "DASIG-ADMIN"));

        assertThat(existing.getOrganizationId()).isNull();
        verify(userRepository, never()).save(any());
    }
}