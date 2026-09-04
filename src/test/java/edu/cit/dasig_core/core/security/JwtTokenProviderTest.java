package edu.cit.dasig_core.core.security;

import edu.cit.dasig_core.features.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-signing";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", 3600_000L);
    }

    private Authentication authenticationFor(String email, String role, boolean mustChangePassword) {
        User user = new User();
        user.setEmail(email);
        user.setName("Test User");
        user.setRole(role);
        user.setPasswordHash("hash");
        user.setStatus("Active");
        user.setMustChangePassword(mustChangePassword);
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void generateToken_embedsSubjectRoleAndMustChangePasswordClaims() {
        Authentication authentication = authenticationFor("user@example.com", "STAFF", true);

        String token = tokenProvider.generateToken(authentication);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(claims.get("role")).isEqualTo("ROLE_STAFF");
        assertThat(claims.get("name")).isEqualTo("Test User");
        assertThat(claims.get("mustChangePassword")).isEqualTo(true);
    }

    @Test
    void generateToken_reflectsMustChangePasswordFalseForAlreadyChangedUsers() {
        Authentication authentication = authenticationFor("user@example.com", "STAFF", false);

        String token = tokenProvider.generateToken(authentication);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.get("mustChangePassword")).isEqualTo(false);
    }

    @Test
    void getUsernameFromJWT_returnsTheSubjectClaim() {
        String token = tokenProvider.generateToken(authenticationFor("someone@example.com", "DASIG_ADMIN", false));

        assertThat(tokenProvider.getUsernameFromJWT(token)).isEqualTo("someone@example.com");
    }

    @Test
    void validateToken_trueForATokenItGenerated() {
        String token = tokenProvider.generateToken(authenticationFor("user@example.com", "STAFF", false));

        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_falseForGarbageInput() {
        assertThat(tokenProvider.validateToken("not-a-real-jwt")).isFalse();
    }

    @Test
    void validateToken_falseForExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000)) // expired 5s ago
                .signWith(key)
                .compact();

        assertThat(tokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateToken_falseForTokenSignedWithADifferentSecret() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("a-completely-different-signing-secret-value-here".getBytes(StandardCharsets.UTF_8));
        String tokenFromAnotherSecret = Jwts.builder()
                .subject("attacker@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(wrongKey)
                .compact();

        assertThat(tokenProvider.validateToken(tokenFromAnotherSecret)).isFalse();
    }
}
