package com.prod.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token Provider for generating and validating JWT tokens.
 *
 * This class handles JWT token generation and validation for the whitelist-based
 * authentication system. Tokens include user ID and email claims, and are signed
 * using HS256 algorithm.
 *
 * Security Features:
 * - Tokens signed with secret key (HS256)
 * - Configurable expiration time
 * - Validation of token signature, expiration, and malformation
 * - Thread-safe token operations
 *
 * @see io.jsonwebtoken.Jwts
 * @see org.springframework.security.core.Authentication
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private SecretKey secretKey;

    /**
     * Initialize the secret key after properties are injected.
     * Converts the configured secret string into a SecretKey instance.
     */
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT Token Provider initialized with expiration: {} ms", jwtExpirationMs);
    }

    /**
     * Generate JWT token for authenticated user.
     *
     * The token contains the following claims:
     * - sub: User ID (UUID)
     * - email: User email address
     * - iat: Issued at timestamp
     * - exp: Expiration timestamp
     *
     * @param userId the user's unique identifier
     * @param email the user's email address
     * @return JWT token string
     */
    public String generateToken(UUID userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        log.debug("Generated JWT token for user: {} (email: {})", userId, email);
        return token;
    }

    /**
     * Validate JWT token signature, expiration, and structure.
     *
     * @param token the JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Extract user ID from JWT token.
     *
     * @param token the JWT token
     * @return the user ID (UUID)
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extract user email from JWT token.
     *
     * @param token the JWT token
     * @return the user email address
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("email", String.class);
    }

    /**
     * Get Authentication object from JWT token.
     *
     * This method creates a Spring Security Authentication object that can be
     * set in the SecurityContextHolder for authenticated requests.
     *
     * @param token the JWT token
     * @return Authentication object with user details
     */
    public Authentication getAuthentication(String token) {
        UUID userId = getUserIdFromToken(token);
        String email = getEmailFromToken(token);

        // Create a simple authentication with user ID as principal and email in details
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );

        authentication.setDetails(email);
        return authentication;
    }

    /**
     * Extract JWT token from Authorization header.
     *
     * Expected header format: "Bearer {token}"
     *
     * @param bearerToken the Authorization header value
     * @return the JWT token string, or null if header is invalid
     */
    public String extractTokenFromHeader(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Get token expiration time in milliseconds.
     *
     * @return expiration time in milliseconds
     */
    public long getExpirationMs() {
        return jwtExpirationMs;
    }
}
