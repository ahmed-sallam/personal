package com.prod.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for login/OTP request.
 *
 * This DTO is used when a user requests to initiate the authentication flow
 * by providing their email address. The system will:
 * 1. Check if the email is whitelisted
 * 2. Generate a 6-digit OTP
 * 3. Store the OTP in Redis with 5-minute TTL
 * 4. Return success (in production: send OTP via email)
 *
 * Validation:
 * - Email must be valid format
 * - Email must not be blank
 *
 * Example JSON request:
 * <pre>
 * {
 *   "email": "user@example.com"
 * }
 * </pre>
 *
 * @see com.prod.dto.request.OTPVerifyRequest
 * @see com.prod.dto.response.AuthResponse
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * User's email address for OTP request.
     *
     * Must be a valid email format and must belong to a whitelisted user
     * in the system. Non-whitelisted emails will receive a 403 Forbidden response.
     *
     * The email is normalized (trimmed and lowercased) before processing.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
}
