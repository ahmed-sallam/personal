package com.prod.controller;

import com.prod.dto.request.LoginRequest;
import com.prod.dto.request.OTPVerifyRequest;
import com.prod.dto.response.AuthResponse;
import com.prod.entity.User;
import com.prod.exception.WhitelistException;
import com.prod.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for authentication endpoints.
 *
 * This controller handles the OTP-based authentication flow for whitelisted users.
 * All endpoints are public (no JWT required) as they are the entry point for authentication.
 *
 * Authentication Flow:
 * 1. User POSTs to /api/auth/login with email
 * 2. System checks whitelist, generates OTP, stores in Redis (5 min TTL)
 * 3. User POSTs to /api/auth/verify with email and OTP
 * 4. System verifies OTP, issues JWT token
 * 5. User uses JWT token in Authorization header for protected endpoints
 *
 * Security Features:
 * - Whitelist validation: Only pre-approved emails can authenticate
 * - Rate limiting: OTP requests limited to prevent brute force (handled by OTPService)
 * - OTP expiration: Codes expire after 5 minutes
 * - JWT tokens: Stateless authentication with configurable expiration
 * - Request validation: Jakarta validation annotations on DTOs
 *
 * Error Responses:
 * - 400 Bad Request: Invalid input (validation errors)
 * - 403 Forbidden: Email not whitelisted (WhitelistException)
 * - 401 Unauthorized: Invalid or expired OTP
 * - 500 Internal Server Error: Unexpected server errors
 *
 * All errors are handled by GlobalExceptionHandler and returned in RFC 7807 format.
 *
 * @see com.prod.service.AuthService
 * @see com.prod.service.OTPService
 * @see com.prod.security.JwtTokenProvider
 * @see com.prod.config.SecurityConfig
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Initiate authentication flow by requesting OTP.
     *
     * This endpoint checks if the provided email is whitelisted and generates
     * a one-time password (OTP) for authentication. The OTP is stored in Redis
     * with a 5-minute expiration time.
     *
     * In production, the OTP would be sent via email. For development purposes,
     * the OTP is included in the response for testing.
     *
     * Endpoint: POST /api/auth/login
     * Authentication: Not required (public endpoint)
     *
     * @param loginRequest the login request containing user's email
     * @return AuthResponse with success message and OTP (development only)
     * @throws WhitelistException if email is not whitelisted
     * @throws IllegalArgumentException if email is invalid
     *
     * Example request:
     * <pre>
     * POST /api/auth/login
     * Content-Type: application/json
     *
     * {
     *   "email": "user@example.com"
     * }
     * </pre>
     *
     * Example response (development):
     * <pre>
     * {
     *   "success": true,
     *   "message": "OTP sent successfully",
     *   "otp": "123456"
     * }
     * </pre>
     *
     * Example response (production):
     * <pre>
     * {
     *   "success": true,
     *   "message": "OTP sent successfully"
     * }
     * </pre>
     *
     * Example error response (non-whitelisted):
     * <pre>
     * {
     *   "type": "https://api.example.com/errors/whitelist",
     *   "title": "Whitelist Validation Failed",
     *   "status": 403,
     *   "detail": "Email 'user@example.com' is not whitelisted for access.",
     *   "instance": "/api/auth/login"
     * }
     * </pre>
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login request received for email: {}", loginRequest.getEmail());

        try {
            // Request OTP generation (includes whitelist check)
            String otp = authService.requestOTP(loginRequest.getEmail());

            log.info("OTP generated successfully for email: {}", loginRequest.getEmail());

            // Build response - include OTP for development only
            AuthResponse response = AuthResponse.builder()
                    .success(true)
                    .message("OTP sent successfully")
                    .otp(otp)  // In production, this should be sent via email instead
                    .build();

            return ResponseEntity.ok(response);

        } catch (WhitelistException e) {
            log.warn("Whitelist validation failed for email: {}", loginRequest.getEmail());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (IllegalArgumentException e) {
            log.warn("Invalid login request: {}", e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (Exception e) {
            log.error("Unexpected error during login for email: {}", loginRequest.getEmail(), e);
            throw e;  // GlobalExceptionHandler will handle this
        }
    }

    /**
     * Verify OTP and generate JWT token.
     *
     * This endpoint verifies the OTP provided by the user and issues a JWT token
     * upon successful verification. The JWT token can then be used to access
     * protected endpoints by including it in the Authorization header.
     *
     * The OTP is deleted from Redis after successful verification to prevent
     * reuse (replay attack prevention).
     *
     * Endpoint: POST /api/auth/verify
     * Authentication: Not required (public endpoint)
     *
     * @param otpVerifyRequest the OTP verification request containing email and OTP
     * @return AuthResponse with JWT token and user information
     * @throws WhitelistException if user is not whitelisted
     * @throws SecurityException if OTP is invalid or expired
     * @throws IllegalArgumentException if input is invalid
     *
     * Example request:
     * <pre>
     * POST /api/auth/verify
     * Content-Type: application/json
     *
     * {
     *   "email": "user@example.com",
     *   "otp": "123456"
     * }
     * </pre>
     *
     * Example response (success):
     * <pre>
     * {
     *   "success": true,
     *   "message": "Authentication successful",
     *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     *   "userId": "550e8400-e29b-41d4-a716-446655440000",
     *   "email": "user@example.com"
     * }
     * </pre>
     *
     * Example error response (invalid OTP):
     * <pre>
     * {
     *   "type": "https://api.example.com/errors/authentication",
     *   "title": "Authentication Failed",
     *   "status": 401,
     *   "detail": "Invalid or expired OTP. Please request a new OTP.",
     *   "instance": "/api/auth/verify"
     * }
     * </pre>
     *
     * After receiving the token, include it in subsequent requests:
     * <pre>
     * GET /api/audio
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     */
    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verifyOTP(@Valid @RequestBody OTPVerifyRequest otpVerifyRequest) {
        log.info("OTP verification requested for email: {}", otpVerifyRequest.getEmail());

        try {
            // Verify OTP and generate JWT token
            String token = authService.verifyOTPAndGenerateToken(
                    otpVerifyRequest.getEmail(),
                    otpVerifyRequest.getOtp()
            );

            // Get user information for response
            UUID userId = authService.getUserIdByEmail(otpVerifyRequest.getEmail());

            log.info("Authentication successful for email: {}, userId: {}",
                    otpVerifyRequest.getEmail(), userId);

            // Build response with token and user info
            AuthResponse response = AuthResponse.builder()
                    .success(true)
                    .message("Authentication successful")
                    .token(token)
                    .userId(userId != null ? userId.toString() : null)
                    .email(otpVerifyRequest.getEmail())
                    .build();

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.warn("OTP verification failed for email: {} - {}",
                    otpVerifyRequest.getEmail(), e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (WhitelistException e) {
            log.warn("Whitelist validation failed during verification for email: {}",
                    otpVerifyRequest.getEmail());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (IllegalArgumentException e) {
            log.warn("Invalid OTP verification request: {}", e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (Exception e) {
            log.error("Unexpected error during OTP verification for email: {}",
                    otpVerifyRequest.getEmail(), e);
            throw e;  // GlobalExceptionHandler will handle this
        }
    }

    /**
     * Health check endpoint for authentication service.
     *
     * This endpoint can be used to verify that the authentication service
     * is operational. It does not require authentication and simply returns
     * a success message.
     *
     * Endpoint: GET /api/auth/health
     * Authentication: Not required (public endpoint)
     *
     * @return AuthResponse indicating service is healthy
     *
     * Example response:
     * <pre>
     * {
     *   "success": true,
     *   "message": "Authentication service is operational"
     * }
     * </pre>
     */
    @GetMapping("/health")
    public ResponseEntity<AuthResponse> healthCheck() {
        return ResponseEntity.ok(
                AuthResponse.builder()
                        .success(true)
                        .message("Authentication service is operational")
                        .build()
        );
    }
}
