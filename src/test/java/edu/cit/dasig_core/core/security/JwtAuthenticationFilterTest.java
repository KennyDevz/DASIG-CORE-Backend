package edu.cit.dasig_core.core.security;

import edu.cit.dasig_core.features.user.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private CustomUserDetailsService customUserDetailsService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "customUserDetailsService", customUserDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User activeUser(boolean mustChangePassword) {
        User user = new User();
        user.setEmail("user@example.com");
        user.setName("Test User");
        user.setRole("STAFF");
        user.setPasswordHash("hash");
        user.setStatus("Active");
        user.setMustChangePassword(mustChangePassword);
        return user;
    }

    private User deactivatedUser() {
        User user = activeUser(false);
        user.setStatus("Inactive");
        return user;
    }

    @Test
    void doFilterInternal_continuesChainWithoutAuthenticationWhenNoTokenPresent() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenProvider, customUserDetailsService);
    }

    @Test
    void doFilterInternal_continuesChainWithoutAuthenticationWhenTokenInvalid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(tokenProvider.validateToken("bad-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(customUserDetailsService);
    }

    @Test
    void doFilterInternal_setsAuthenticationForValidTokenAndActiveUserThatDoesNotNeedPasswordChange() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(tokenProvider.validateToken("good-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromJWT("good-token")).thenReturn("user@example.com");
        when(customUserDetailsService.loadUserByUsername("user@example.com"))
                .thenReturn(new CustomUserPrincipal(activeUser(false)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user@example.com");
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_deactivatedUsersExistingTokenNeverAuthenticates() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer old-token");
        when(tokenProvider.validateToken("old-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromJWT("old-token")).thenReturn("user@example.com");
        when(customUserDetailsService.loadUserByUsername("user@example.com"))
                .thenReturn(new CustomUserPrincipal(deactivatedUser()));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // Falls through to the rest of the chain unauthenticated - Spring Security's
        // .anyRequest().authenticated() then rejects it with 401, same as no token at all.
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_blocksNonExemptEndpointWhenPasswordChangeIsRequired() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer temp-token");
        when(request.getRequestURI()).thenReturn("/api/dashboard");
        when(tokenProvider.validateToken("temp-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromJWT("temp-token")).thenReturn("user@example.com");
        when(customUserDetailsService.loadUserByUsername("user@example.com"))
                .thenReturn(new CustomUserPrincipal(activeUser(true)));

        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(body.toString()).contains("must change your temporary password");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_allowsThePasswordChangeEndpointItselfWhenPasswordChangeIsRequired() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer temp-token");
        when(request.getRequestURI()).thenReturn("/api/account/password");
        when(tokenProvider.validateToken("temp-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromJWT("temp-token")).thenReturn("user@example.com");
        when(customUserDetailsService.loadUserByUsername("user@example.com"))
                .thenReturn(new CustomUserPrincipal(activeUser(true)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_allowsAuthEndpointsWhenPasswordChangeIsRequired() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer temp-token");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(tokenProvider.validateToken("temp-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromJWT("temp-token")).thenReturn("user@example.com");
        when(customUserDetailsService.loadUserByUsername("user@example.com"))
                .thenReturn(new CustomUserPrincipal(activeUser(true)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ignoresAuthorizationHeaderWithoutBearerPrefix() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenProvider);
    }
}
