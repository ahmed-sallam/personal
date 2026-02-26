package com.prod.service;

import com.prod.entity.User;
import com.prod.exception.WhitelistException;
import com.prod.repository.UserRepository;
import com.prod.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for handling whitelist-based authentication with OTP and JWT flow.
 *
 * This service implements the complete authentication flow for the productivity
 * tracking system. Only whitelisted users can authenticate using OTP-based
 * authentication (no passwords). The flow consists of:
 *
 * 1. Login Request (requestOTP):
 *    - User provides email address
 *    - Service checks if user is whitelisted
 *    - If whitelisted, generate OTP and store in Redis
 *    - Return success (in production: send OTP via email)
 *
 * 2. OTP Verification (verifyOTPAndGenerateToken):
 *    - User provides email and OTP
 *    - Service verifies OTP from Redis
 *    - If valid, generate JWT token
 *    - Clear OTP from Redis
 *    - Return JWT token to client
 *
 * 3. JWT Usage:
 *    - Client includes JWT in Authorization header
 *    - JwtAuthenticationFilter validates token on each request
 *    - User can access protected endpoints
 *
 * Security Features:
 * - Whitelist validation: Only pre-approved users can authenticate
 * - OTP-based: No passwords stored, OTP expires after 5 minutes
 * - Rate limiting: OTP requests limited to prevent brute force (handled by OTPService)
 * - JWT tokens: Stateless authentication with configurable expiration
 * - Thread-safe: All operations are thread-safe
 *
 * @see com.prod.service.OTPService
 * @see com.prod.security.JwtTokenProvider
 * @see com.prod.entity.User
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OTPService otpService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Request OTP for authentication with whitelist check.
     *
     * This method performs the following steps:
     * 1. Validate email is not null/empty
     * 2. Check if user exists in database
     * 3. Verify user is whitelisted (is_whitelisted = true)
     * 4. Generate and store OTP in Redis (via OTPService)
     * 5. Log the authentication attempt
     *
     * If the user is not found or not whitelisted, WhitelistException is thrown.
     *
     * Note: In production, the OTP should be sent via email. For development,
     * the OTP is returned in the response or logged to console.
     *
     * @param email the user's email address
     * @return the generated OTP code (for development purposes)
     * @throws WhitelistException if user does not exist or is not whitelisted
     * @throws IllegalArgumentException if email is null or empty
     */
    public String requestOTP(String email) {
        // Validate input
        if (email == null || email.trim().isEmpty()) {
            log.warn("Attempted to request OTP with null or empty email");
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        // Normalize email (trim and lowercase)
        String normalizedEmail = email.trim().toLowerCase();
        log.info("OTP request received for email: {}", normalizedEmail);

        // Check if user exists and is whitelisted
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("OTP requested for non-existent email: {}", normalizedEmail);
                    return WhitelistException.userNotFound(normalizedEmail);
                });

        if (!user.getIsWhitelisted()) {
            log.warn("OTP requested for non-whitelisted user: {}", normalizedEmail);
            throw WhitelistException.forEmail(normalizedEmail);
        }

        // Generate and store OTP
        String otp = otpService.generateAndStoreOTP(normalizedEmail);

        log.info("OTP generated successfully for whitelisted user: {} (valid for {} minutes)",
                normalizedEmail, otpService.getOTPExpirationMinutes());

        // In production, send OTP via email here
        // For development, return the OTP for testing
        return otp;
    }

    /**
     * Verify OTP and generate JWT token for authenticated user.
     *
     * This method performs the following steps:
     * 1. Validate email and OTP are not null/empty
     * 2. Verify OTP from Redis (via OTPService)
     * 3. If valid, retrieve user from database
     * 4. Generate JWT token with user ID and email
     * 5. Log successful authentication
     *
     * The JWT token includes:
     * - Subject: User ID (UUID)
     * - Claim "email": User email address
     * - Issued at timestamp
     * - Expiration timestamp (configured via jwt.expiration)
     * - Signature: HS256 with secret key
     *
     * After successful verification, the OTP is deleted from Redis to prevent
     * reuse (handled by OTPService.verifyOTP).
     *
     * @param email the user's email address
     * @param otp the OTP code to verify
     * @return JWT token string
     * @throws IllegalArgumentException if email or OTP is null/empty
     * @throws WhitelistException if user does not exist or is not whitelisted
     * @throws SecurityException if OTP is invalid or expired
     */
    public String verifyOTPAndGenerateToken(String email, String otp) {
        // Validate input
        if (email == null || email.trim().isEmpty()) {
            log.warn("Attempted to verify OTP with null or empty email");
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (otp == null || otp.trim().isEmpty()) {
            log.warn("Attempted to verify OTP with null or empty OTP code");
            throw new IllegalArgumentException("OTP cannot be null or empty");
        }

        // Normalize email
        String normalizedEmail = email.trim().toLowerCase();
        log.info("OTP verification requested for email: {}", normalizedEmail);

        // Verify OTP
        boolean isOtpValid = otpService.verifyOTP(normalizedEmail, otp);

        if (!isOtpValid) {
            log.warn("Invalid or expired OTP provided for email: {}", normalizedEmail);
            throw new SecurityException("Invalid or expired OTP. Please request a new OTP.");
        }

        // OTP is valid, get user from database
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.error("User not found during token generation for email: {}", normalizedEmail);
                    return WhitelistException.userNotFound(normalizedEmail);
                });

        // Double-check whitelist status
        if (!user.getIsWhitelisted()) {
            log.error("User lost whitelist status during authentication flow: {}", normalizedEmail);
            throw WhitelistException.forEmail(normalizedEmail);
        }

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());

        log.info("Authentication successful for user: {} (ID: {})", normalizedEmail, user.getId());
        log.debug("JWT token generated with expiration: {} ms", jwtTokenProvider.getExpirationMs());

        return token;
    }

    /**
     * Check if a user is whitelisted without generating OTP.
     *
     * This method can be used to validate whitelist status before initiating
     * the OTP flow, providing better UX by failing fast for non-whitelisted users.
     *
     * @param email the user's email address
     * @return true if user exists and is whitelisted, false otherwise
     */
    public boolean isWhitelisted(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.findByEmailAndIsWhitelisted(normalizedEmail, true).isPresent();
    }

    /**
     * Get user ID by email address.
     *
     * This utility method is useful for logging and user identification
     * in other parts of the application.
     *
     * @param email the user's email address
     * @return the user's UUID, or null if not found
     */
    public UUID getUserIdByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.findByEmail(normalizedEmail)
                .map(User::getId)
                .orElse(null);
    }

    /**
     * Refresh JWT token for an authenticated user.
     *
     * This method allows users to refresh their JWT token before expiration
     * without going through the OTP flow again. The user must provide a valid
     * JWT token, which is used to identify the user.
     *
     * Note: This method should be called from an authenticated endpoint where
     * the user's identity is already verified via the JWT filter.
     *
     * @param userId the user's ID from the current JWT token
     * @param email the user's email from the current JWT token
     * @return new JWT token
     * @throws WhitelistException if user no longer exists or is not whitelisted
     */
    public String refreshToken(UUID userId, String email) {
        log.info("Token refresh requested for user ID: {}, email: {}", userId, email);

        // Verify user still exists and is whitelisted
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Token refresh failed: user not found for ID: {}", userId);
                    return WhitelistException.userNotFound(email);
                });

        if (!user.getIsWhitelisted()) {
            log.warn("Token refresh failed: user lost whitelist status: {}", email);
            throw WhitelistException.forEmail(email);
        }

        // Verify email matches (prevent token reuse across users)
        if (!user.getEmail().equalsIgnoreCase(email)) {
            log.warn("Token refresh failed: email mismatch for user ID: {}", userId);
            throw new SecurityException("Email does not match user ID");
        }

        // Generate new token
        String newToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail());

        log.info("Token refreshed successfully for user: {}", email);
        return newToken;
    }
}
