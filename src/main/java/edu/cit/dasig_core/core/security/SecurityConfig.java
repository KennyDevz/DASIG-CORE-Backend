package edu.cit.dasig_core.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // As specified in your SRS, passwords must be hashed using bcrypt
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. Disable CSRF since we are building a stateless REST API
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. Set session management to stateless (React will use Tokens later, not server sessions)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 3. Configure endpoint routing
            .authorizeHttpRequests(auth -> auth
                // Allow anyone to access the auth/login endpoints (which we haven't built yet)
                .requestMatchers("/api/auth/**").permitAll()
                // All other API endpoints require the user to be authenticated
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
