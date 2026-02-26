package com.prod.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API endpoints.
 *
 * This class provides centralized exception handling across all controllers,
 * converting exceptions into RFC 7807 compliant error responses. RFC 7807
 * (Problem Details for HTTP APIs) is a standardized format for error responses
 * that provides machine-readable details about errors.
 *
 * <p>RFC 7807 Response Format:
 * <pre>
 * {
 *   "type": "https://api.example.com/errors/validation-failed",
 *   "title": "Validation Failed",
 *   "status": 400,
 *   "detail": "Email is required",
 *   "instance": "/api/auth/login",
 *   "timestamp": "2024-02-26T10:30:00Z"
 * }
 * </pre>
 *
 * <p>Handled Exception Categories:
 * <ul>
 *   <li>Authentication errors (401): Invalid/expired tokens, bad credentials</li>
 *   <li>Authorization errors (403): Whitelist violations, access denied</li>
 *   <li>Validation errors (400): Invalid request format, constraint violations</li>
 *   <li>Not found errors (404): Invalid endpoints, missing resources</li>
 *   <li>Processing errors (500): AI/ML pipeline failures</li>
 *   <li>Server errors (500): Unexpected internal errors</li>
 * </ul>
 *
 * <p>Error Type URI Convention:
 * <pre>
 * https://api.prod.com/errors/{error-category}
 * </pre>
 *
 * Example categories:
 * - authentication-failed
 * - whitelist-violation
 * - validation-failed
 * - resource-not-found
 * - processing-failed
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7807">RFC 7807 Specification</a>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String BASE_ERROR_URI = "https://api.prod.com/errors";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Handles WhitelistException - user is not whitelisted for access.
     *
     * This occurs when a non-whitelisted user attempts to request OTP or authenticate.
     * Mapped to HTTP 403 Forbidden.
     *
     * @param ex the WhitelistException
     * @param request the web request context
     * @return RFC 7807 problem details with 403 status
     */
    @ExceptionHandler(WhitelistException.class)
    public ResponseEntity<ProblemDetail> handleWhitelistException(
            WhitelistException ex,
            WebRequest request
    ) {
        log.warn("Whitelist validation failed: {}", ex.getMessage());

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.FORBIDDEN,
                "Whitelist Violation",
                ex.getMessage(),
                request,
                "whitelist-violation"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    /**
     * Handles UnauthorizedException - authenticated user lacks authorization.
     *
     * This occurs when an authenticated user attempts to access resources
     * they don't have permission to access (e.g., cross-user access).
     * Mapped to HTTP 403 Forbidden.
     *
     * @param ex the UnauthorizedException
     * @param request the web request context
     * @return RFC 7807 problem details with 403 status
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorizedException(
            UnauthorizedException ex,
            WebRequest request
    ) {
        log.warn("Authorization failed: {}", ex.getMessage());

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                ex.getMessage(),
                request,
                "access-denied"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    /**
     * Handles ProcessingException - audio processing pipeline failures.
     *
     * This occurs during Whisper transcription, Claude analysis, or queue operations.
     * Mapped to HTTP 500 Internal Server Error (or 503 for infrastructure issues).
     *
     * @param ex the ProcessingException
     * @param request the web request context
     * @return RFC 7807 problem details with 500/503 status
     */
    @ExceptionHandler(ProcessingException.class)
    public ResponseEntity<ProblemDetail> handleProcessingException(
            ProcessingException ex,
            WebRequest request
    ) {
        log.error("Audio processing failed: stage={}, error={}, message={}",
                ex.getProcessingStage(), ex.getErrorCode(), ex.getMessage(), ex);

        // Use 503 Service Unavailable for queue issues, 500 otherwise
        HttpStatus status = "QUEUE_UNAVAILABLE".equals(ex.getErrorCode())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.INTERNAL_SERVER_ERROR;

        ProblemDetail problemDetail = createProblemDetail(
                status,
                "Processing Failed",
                ex.getMessage(),
                request,
                "processing-failed"
        );

        // Add processing-specific properties
        if (ex.getProcessingStage() != null) {
            problemDetail.setProperty("processingStage", ex.getProcessingStage());
        }
        if (ex.getErrorCode() != null) {
            problemDetail.setProperty("errorCode", ex.getErrorCode());
        }

        return ResponseEntity.status(status).body(problemDetail);
    }

    /**
     * Handles BadCredentialsException - invalid authentication credentials.
     *
     * This occurs when OTP is invalid or expired during authentication.
     * Mapped to HTTP 401 Unauthorized.
     *
     * @param ex the BadCredentialsException
     * @param request the web request context
     * @return RFC 7807 problem details with 401 status
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentialsException(
            BadCredentialsException ex,
            WebRequest request
    ) {
        log.warn("Authentication failed: {}", ex.getMessage());

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed",
                "Invalid or expired authentication credentials. Please provide a valid OTP.",
                request,
                "authentication-failed"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    /**
     * Handles AccessDeniedException - Spring Security access denial.
     *
     * This occurs when authentication is required but not provided.
     * Mapped to HTTP 401 Unauthorized.
     *
     * @param ex the AccessDeniedException
     * @param request the web request context
     * @return RFC 7807 problem details with 401 status
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request
    ) {
        log.warn("Access denied: {}", ex.getMessage());

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "Authentication Required",
                "You must be authenticated to access this resource. Please provide a valid JWT token.",
                request,
                "authentication-required"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    /**
     * Handles MethodArgumentNotValidException - bean validation failures.
     *
     * This occurs when @Valid annotated request bodies fail validation.
     * Mapped to HTTP 400 Bad Request.
     *
     * @param ex the MethodArgumentNotValidException
     * @param request the web request context
     * @return RFC 7807 problem details with 400 status and validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        log.warn("Request validation failed: {}", ex.getMessage());

        // Build validation error details
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "Request validation failed. Please check the 'errors' property for details.",
                request,
                "validation-failed"
        );

        problemDetail.setProperty("errors", validationErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles HttpMessageNotReadableException - malformed request body.
     *
     * This occurs when JSON parsing fails or request body is invalid.
     * Mapped to HTTP 400 Bad Request.
     *
     * @param ex the HttpMessageNotReadableException
     * @param request the web request context
     * @return RFC 7807 problem details with 400 status
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            WebRequest request
    ) {
        log.warn("Request body parsing failed: {}", ex.getMessage());

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid Request Body",
                "The request body is malformed or contains invalid JSON. Please check your request format.",
                request,
                "invalid-request-body"
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles NoHandlerFoundException - invalid endpoint.
     *
     * This occurs when a non-existent endpoint is requested.
     * Mapped to HTTP 404 Not Found.
     *
     * @param ex the NoHandlerFoundException
     * @param request the web request context
     * @return RFC 7807 problem details with 404 status
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoHandlerFoundException(
            NoHandlerFoundException ex,
            WebRequest request
    ) {
        log.warn("Endpoint not found: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.NOT_FOUND,
                "Endpoint Not Found",
                String.format("The requested endpoint '%s %s' does not exist.",
                        ex.getHttpMethod(), ex.getRequestURL()),
                request,
                "endpoint-not-found"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Handles IllegalArgumentException - illegal argument passed to a method.
     *
     * This occurs when invalid arguments are passed to service methods.
     * Mapped to HTTP 400 Bad Request.
     *
     * @param ex the IllegalArgumentException
     * @param request the web request context
     * @return RFC 7807 problem details with 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        log.warn("Invalid argument: {}", ex.getMessage());

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid Argument",
                ex.getMessage(),
                request,
                "invalid-argument"
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles SecurityException - security-related errors.
     *
     * This occurs for various security violations during processing.
     * Mapped to HTTP 401 Unauthorized or 403 Forbidden based on context.
     *
     * @param ex the SecurityException
     * @param request the web request context
     * @return RFC 7807 problem details with 401 status
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> handleSecurityException(
            SecurityException ex,
            WebRequest request
    ) {
        log.warn("Security violation: {}", ex.getMessage());

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "Security Violation",
                ex.getMessage(),
                request,
                "security-violation"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    /**
     * Fallback handler for all unhandled exceptions.
     *
     * This catches any exception not specifically handled above and returns
     * a generic 500 error. Logs the full stack trace for debugging.
     * Mapped to HTTP 500 Internal Server Error.
     *
     * @param ex the unhandled exception
     * @param request the web request context
     * @return RFC 7807 problem details with 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnhandledException(
            Exception ex,
            WebRequest request
    ) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later or contact support if the issue persists.",
                request,
                "internal-error"
        );

        // Don't expose stack traces in production
        // In development, you might want to include the exception message
        problemDetail.setProperty("errorId", generateErrorId());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    /**
     * Creates a ProblemDetail object with RFC 7807 compliant fields.
     *
     * @param status the HTTP status code
     * @param title a short, human-readable title
     * @param detail a detailed explanation
     * @param request the web request context
     * @param errorType the error type identifier for the type URI
     * @return a populated ProblemDetail object
     */
    private ProblemDetail createProblemDetail(
            HttpStatus status,
            String title,
            String detail,
            WebRequest request,
            String errorType
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);

        // Set RFC 7807 required fields
        problemDetail.setType(URI.create(String.format("%s/%s", BASE_ERROR_URI, errorType)));
        problemDetail.setTitle(title);
        problemDetail.setStatus(status.value());

        // Set instance (the request path where error occurred)
        String description = request.getDescription(false);
        if (description != null && description.startsWith("uri=")) {
            String path = description.substring(4);
            problemDetail.setInstance(URI.create(path));
        }

        // Add custom timestamp property
        problemDetail.setProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));

        return problemDetail;
    }

    /**
     * Generates a unique error ID for tracking and debugging.
     *
     * @return a unique error identifier
     */
    private String generateErrorId() {
        return String.format("ERR-%d", System.currentTimeMillis());
    }
}
