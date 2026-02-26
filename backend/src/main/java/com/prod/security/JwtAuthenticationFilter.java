package com.prod.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter for request validation.
 *
 * This filter intercepts every HTTP request and validates JWT tokens from the
 * Authorization header. If a valid token is present, it sets the authentication
 * in the SecurityContextHolder.
 *
 * Filter Execution Flow:
 * 1. Extract JWT token from Authorization header (Bearer {token})
 * 2. Validate token signature and expiration
 * 3. If valid, create Authentication object and set in SecurityContext
 * 4. Pass request to next filter in chain
 *
 * Security Notes:
 * - Runs once per request (extends OncePerRequestFilter)
 * - Does not block requests without tokens (allows public endpoints)
 * - Logs security events for audit purposes
 * - Thread-safe (SecurityContext is thread-local)
 *
 * @see OncePerRequestFilter
 * @see JwtTokenProvider
 * @see org.springframework.security.core.context.SecurityContextHolder
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Filter internal implementation for JWT authentication.
     *
     * This method is called once per request to validate JWT tokens and
     * set authentication context if valid.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain to continue execution
     * @throws ServletException if a servlet error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // Extract JWT token from Authorization header
            String bearerToken = request.getHeader("Authorization");
            String token = jwtTokenProvider.extractTokenFromHeader(bearerToken);

            // Validate token and set authentication if valid
            if (token != null && jwtTokenProvider.validateToken(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Set authentication for user: {} on path: {}",
                        authentication.getPrincipal(),
                        request.getRequestURI());
            } else if (token != null) {
                // Token present but invalid
                log.warn("Invalid JWT token on path: {}", request.getRequestURI());
            }
        } catch (Exception ex) {
            // Log error but don't block request - SecurityContext will be empty
            // and Spring Security will handle authorization based on security config
            log.error("Cannot set user authentication: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        // Always continue the filter chain, even if authentication fails
        // Security authorization will be handled by SecurityConfig
        filterChain.doFilter(request, response);
    }

    /**
     * Determines if this filter should be applied to the current request.
     *
     * Override this method if you need to skip filtering for specific paths
     * (e.g., static resources, health checks).
     *
     * Current Implementation: Applies to all requests except Spring Boot's
     * error handling paths.
     *
     * @param request the current HTTP request
     * @return false to skip filtering, true to apply filter
     * @throws ServletException if an error occurs
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        // Skip JWT validation for error paths (Spring Boot internal error handling)
        if (path.startsWith("/error")) {
            return true;
        }

        // Apply filter to all other paths
        // Public endpoints (e.g., /api/auth/**) will still go through this filter,
        // but SecurityConfig will allow them without authentication
        return false;
    }
}
