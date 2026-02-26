package com.prod.config;

import com.prod.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for JWT-based authentication.
 *
 * This class configures the security filter chain for the application, implementing
 * a whitelist-based authentication system with JWT tokens. Key security features:
 *
 * - JWT authentication via custom filter ( JwtAuthenticationFilter)
 * - Public endpoints for authentication (/api/auth/**)
 * - Protected endpoints requiring valid JWT token
 * - Stateless session management (no server-side sessions)
 * - CSRF disabled (suitable for JWT-based API)
 * - CORS enabled for frontend integration
 * - Method-level security enabled (@PreAuthorize, @Secured)
 *
 * Authentication Flow:
 * 1. User requests OTP via /api/auth/login (public endpoint)
 * 2. User verifies OTP via /api/auth/verify (public endpoint)
 * 3. User receives JWT token
 * 4. User includes JWT token in Authorization header (Bearer {token})
 * 5. JwtAuthenticationFilter validates token on each request
 * 6. Protected endpoints accessible only with valid token
 *
 * Security Considerations:
 * - All requests to protected endpoints must include valid JWT
 * - JWT tokens are signed with secret key (HS256)
 * - Token expiration is enforced by JwtTokenProvider
 * - Failed authentication returns 401 Unauthorized
 * - Authorization failures return 403 Forbidden
 *
 * @see org.springframework.security.config.annotation.web.builders.HttpSecurity
 * @see com.prod.security.JwtAuthenticationFilter
 * @see org.springframework.security.web.SecurityFilterChain
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configure the security filter chain for JWT-based authentication.
     *
     * This method sets up the HTTP security configuration with:
     * - CSRF protection disabled (suitable for stateless JWT authentication)
     * - Public endpoints: /api/auth/**, /actuator/health, /actuator/info
     * - All other endpoints require authentication
     * - Stateless session management (no JSESSIONID)
     * - JWT filter added before UsernamePasswordAuthenticationFilter
     * - CORS enabled (configured in separate CorsConfig)
     *
     * @param http the HttpSecurity builder to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF (not needed for JWT-based stateless authentication)
                .csrf(AbstractHttpConfigurer::disable)

                // Configure endpoint authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints (OTP request and verification)
                        .requestMatchers(
                                "/api/auth/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Configure stateless session management (no server-side sessions)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                // This ensures JWT validation happens before Spring Security's
                // default authentication mechanisms
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Note: CORS configuration is handled in a separate CorsConfig class.
     *
     * This separation of concerns allows for more flexible CORS policy management,
     * especially when supporting multiple frontend origins or different environments
     * (development, staging, production).
     *
     * @see com.prod.config.CorsConfig
     */
}
