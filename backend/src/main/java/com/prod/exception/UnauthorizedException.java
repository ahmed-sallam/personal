package com.prod.exception;

/**
 * Exception thrown when a user attempts to access a resource without proper authorization.
 *
 * This exception is distinct from authentication failures. While authentication failures
 * (401) occur when a user cannot prove their identity (invalid/missing JWT), authorization
 * failures (403) occur when an authenticated user attempts to access a resource they
 * do not have permission to access.
 *
 * Usage examples:
 * - User attempts to access another user's audio records
 * - User attempts to modify processing tasks they don't own
 * - User attempts to access admin-only endpoints
 *
 * GlobalExceptionHandler maps this to HTTP 403 Forbidden with RFC 7807 format.
 *
 * @see com.prod.exception.GlobalExceptionHandler
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * Constructs a new UnauthorizedException with the specified detail message.
     *
     * @param message the detail message explaining the authorization failure
     */
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnauthorizedException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new UnauthorizedException for accessing another user's resource.
     *
     * @param resourceType the type of resource (e.g., "audio record", "summary")
     * @param resourceId the ID of the resource
     * @return an UnauthorizedException with a formatted message
     */
    public static UnauthorizedException accessDenied(String resourceType, String resourceId) {
        return new UnauthorizedException(
                String.format("Access denied to %s '%s'. You do not have permission to access this resource.",
                        resourceType, resourceId)
        );
    }

    /**
     * Constructs a new UnauthorizedException for accessing another user's resources.
     *
     * @param resourceType the type of resource (e.g., "audio records", "summaries")
     * @return an UnauthorizedException with a formatted message
     */
    public static UnauthorizedException userResourceAccessDenied(String resourceType) {
        return new UnauthorizedException(
                String.format("Access denied. You can only access your own %s.", resourceType)
        );
    }

    /**
     * Constructs a new UnauthorizedException for insufficient permissions.
     *
     * @param action the action that was attempted (e.g., "delete", "modify")
     * @param resourceType the type of resource
     * @return an UnauthorizedException with a formatted message
     */
    public static UnauthorizedException insufficientPermissions(String action, String resourceType) {
        return new UnauthorizedException(
                String.format("Insufficient permissions to %s %s.", action, resourceType)
        );
    }

    /**
     * Constructs a new UnauthorizedException for cross-user access attempts.
     *
     * @param userId the ID of the user whose resource was accessed
     * @return an UnauthorizedException with a formatted message
     */
    public static UnauthorizedException crossUserAccess(String userId) {
        return new UnauthorizedException(
                String.format("Cross-user access denied. You cannot access resources belonging to user '%s'.", userId)
        );
    }
}
