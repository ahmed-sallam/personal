package com.prod.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for authentication operations.
 *
 * This DTO is returned to clients after successful authentication operations:
 * 1. Login request (OTP generation) - Returns success message and OTP (dev only)
 * 2. OTP verification - Returns JWT token
 *
 * In production, the OTP field should only be populated for development/testing.
 * The actual OTP would be sent via email in production environment.
 *
 * Example JSON response for login:
 * <pre>
 * {
 *   "success": true,
 *   "message": "OTP sent successfully",
 *   "otp": "123456"
 * }
 * </pre>
 *
 * Example JSON response for verification:
 * <pre>
 * {
 *   "success": true,
 *   "message": "Authentication successful",
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
 * }
 * </pre>
 *
 * The JWT token should be included in the Authorization header for subsequent requests:
 * <pre>
 * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
 * </pre>
 *
 * @see com.prod.dto.request.LoginRequest
 * @see com.prod.dto.request.OTPVerifyRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * Indicates whether the authentication operation was successful.
     * true if the operation succeeded, false otherwise.
     */
    private boolean success;

    /**
     * Human-readable message describing the result of the operation.
     *
     * For successful login: "OTP sent successfully"
     * For successful verification: "Authentication successful"
     * For errors: Exception message from service layer
     */
    private String message;

    /**
     * JWT token issued after successful OTP verification.
     *
     * This token is only populated when OTP verification succeeds.
     * The token includes:
     * - Subject: User ID (UUID)
     * - Claim "email": User email address
     * - Issued at timestamp
     * - Expiration timestamp (configured via jwt.expiration)
     *
     * Clients should include this token in the Authorization header:
     * Authorization: Bearer {token}
     */
    private String token;

    /**
     * OTP code generated for the user.
     *
     * IMPORTANT: This field should only be populated in development environments
     * for testing purposes. In production, the OTP should be sent via email
     * and this field should be null or omitted from the response.
     *
     * The OTP is a 6-digit numeric code with 5-minute expiration.
     */
    private String otp;

    /**
     * User ID (UUID) of the authenticated user.
     *
     * This field is populated after successful OTP verification.
     * It can be used by the frontend to identify the current user.
     */
    private String userId;

    /**
     * Email address of the authenticated user.
     *
     * This field is populated after successful OTP verification.
     * It can be used by the frontend to display the current user's email.
     */
    private String email;
}
