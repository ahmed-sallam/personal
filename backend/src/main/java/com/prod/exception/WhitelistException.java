package com.prod.exception;

/**
 * Exception thrown when a user is not whitelisted for authentication.
 *
 * This exception is thrown when attempting to authenticate with an email
 * address that is not in the system's whitelist. Only whitelisted users
 * are allowed to request OTP and authenticate to the system.
 *
 * Usage in authentication flow:
 * 1. User provides email for login
 * 2. AuthService checks if email exists in database with is_whitelisted = true
 * 3. If not whitelisted, throw WhitelistException
 * 4. GlobalExceptionHandler maps this to HTTP 403 Forbidden
 */
public class WhitelistException extends RuntimeException {

    /**
     * Constructs a new WhitelistException with the specified detail message.
     *
     * @param message the detail message explaining which email is not whitelisted
     */
    public WhitelistException(String message) {
        super(message);
    }

    /**
     * Constructs a new WhitelistException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public WhitelistException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new WhitelistException for a specific email address.
     *
     * @param email the email address that is not whitelisted
     * @return a WhitelistException with a formatted message
     */
    public static WhitelistException forEmail(String email) {
        return new WhitelistException(
                String.format("Email '%s' is not whitelisted for access. Only pre-approved users can authenticate.", email)
        );
    }

    /**
     * Constructs a new WhitelistException when user does not exist.
     *
     * @param email the email address that was not found
     * @return a WhitelistException with a formatted message
     */
    public static WhitelistException userNotFound(String email) {
        return new WhitelistException(
                String.format("User with email '%s' does not exist in the system.", email)
        );
    }
}
