package com.prod.controller;

import com.prod.dto.response.SummaryResponse;
import com.prod.service.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for AI summary endpoints.
 *
 * This controller handles retrieval of AI-generated summaries for authenticated
 * users. All endpoints require JWT authentication and enforce user isolation
 * to ensure users can only access their own summaries.
 *
 * Summary Data:
 * Summaries are generated asynchronously by the audio processing pipeline:
 * 1. User uploads audio → AudioRecord created
 * 2. RabbitMQ consumer processes audio → Whisper transcribes
 * 3. Claude analyzes transcription → Structured data extracted
 * 4. AISummary created with transcription and structured data
 * 5. User retrieves summary via this controller
 *
 * Security Features:
 * - JWT authentication required for all endpoints
 * - User isolation: Users can only access their own summaries
 * - Authorization checks: Each request validates user ownership
 * - Input validation: UUID format validation for path variables
 *
 * Error Responses:
 * - 400 Bad Request: Invalid input (malformed UUID, invalid pagination)
 * - 401 Unauthorized: Missing or invalid JWT token
 * - 403 Forbidden: Attempting to access another user's summaries
 * - 404 Not Found: Summary or audio record not found
 * - 500 Internal Server Error: Unexpected server errors
 *
 * All errors are handled by GlobalExceptionHandler and returned in RFC 7807 format.
 *
 * @see com.prod.service.SummaryService
 * @see com.prod.dto.response.SummaryResponse
 * @see com.prod.config.SecurityConfig
 */
@RestController
@RequestMapping("/api/summaries")
@RequiredArgsConstructor
@Slf4j
public class SummaryController {

    private final SummaryService summaryService;

    /**
     * Get all summaries for the authenticated user with pagination.
     *
     * This endpoint returns a paginated list of all AI summaries associated with
     * audio records owned by the authenticated user. Results are ordered by
     * creation date (newest first) and include both the raw transcription and
     * structured data extracted by Claude.
     *
     * Endpoint: GET /api/summaries
     * Authentication: Required (JWT token)
     *
     * Query Parameters:
     * - page: Page number (default: 0, minimum: 0)
     * - size: Items per page (default: 20, minimum: 1, maximum: 100)
     * - sort: Sort field (default: createdAt,desc)
     *
     * @param authentication the Spring Security authentication object (from JWT)
     * @param page page number (0-based, default: 0)
     * @param size page size (default: 20)
     * @return Page of SummaryResponse DTOs
     * @throws IllegalArgumentException if pagination parameters are invalid
     *
     * Example request:
     * <pre>
     * GET /api/summaries?page=0&size=20
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     *
     * Example response (200 OK):
     * <pre>
     * {
     *   "content": [
     *     {
     *       "id": "a1234567-e89b-12d3-a456-426614174000",
     *       "audioRecordId": "550e8400-e29b-41d4-a716-446655440000",
     *       "content": "In today's meeting, we discussed...",
     *       "structuredData": {
     *         "event": "Requirements meeting",
     *         "duration_minutes": 40,
     *         "category": "meeting",
     *         "action_items": ["Review specs", "Schedule follow-up"]
     *       },
     *       "tokensUsed": 1250,
     *       "model": "anthropic/claude-3.5-sonnet",
     *       "createdAt": "2024-01-15T10:31:00"
     *     }
     *   ],
     *   "pageable": {
     *     "pageNumber": 0,
     *     "pageSize": 20,
     *     "totalElements": 45,
     *     "totalPages": 3
     *   }
     * }
     * </pre>
     *
     * Example error response (unauthorized):
     * <pre>
     * {
     *   "type": "https://api.example.com/errors/unauthorized",
     *   "title": "Unauthorized",
     *   "status": 401,
     *   "detail": "Full authentication is required to access this resource",
     *   "instance": "/api/summaries"
     * }
     * </pre>
     */
    @GetMapping
    public ResponseEntity<Page<SummaryResponse>> getSummaries(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Summaries requested by user: {}, page: {}, size: {}",
                authentication != null ? authentication.getName() : "anonymous", page, size);

        try {
            // Validate pagination parameters
            if (page < 0) {
                log.warn("Invalid page number: {}", page);
                throw new IllegalArgumentException("Page number must be >= 0");
            }
            if (size < 1 || size > 100) {
                log.warn("Invalid page size: {}", size);
                throw new IllegalArgumentException("Page size must be between 1 and 100");
            }

            // Create pageable with default sorting by creation date (newest first)
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

            // Get summaries for authenticated user
            Page<SummaryResponse> summaries = summaryService.getSummariesForUser(authentication, pageable);

            log.info("Returning {} summaries for user: {} (page {} of {}, total: {})",
                    summaries.getNumberOfElements(),
                    authentication.getName(),
                    summaries.getNumber(),
                    summaries.getTotalPages(),
                    summaries.getTotalElements());

            return ResponseEntity.ok(summaries);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for summaries: {}", e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (Exception e) {
            log.error("Unexpected error retrieving summaries for user: {}",
                    authentication.getName(), e);
            throw e;  // GlobalExceptionHandler will handle this
        }
    }

    /**
     * Get a specific summary by ID.
     *
     * This endpoint returns a single AI summary by its ID, but only if it
     * belongs to an audio record owned by the authenticated user. This prevents
     * users from accessing summaries generated by other users.
     *
     * Endpoint: GET /api/summaries/{id}
     * Authentication: Required (JWT token)
     *
     * @param id the summary ID (UUID)
     * @param authentication the Spring Security authentication object (from JWT)
     * @return SummaryResponse with complete summary details
     * @throws IllegalArgumentException if UUID format is invalid
     * @throws SecurityException if summary doesn't belong to authenticated user
     *
     * Example request:
     * <pre>
     * GET /api/summaries/a1234567-e89b-12d3-a456-426614174000
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     *
     * Example response (200 OK):
     * <pre>
     * {
     *   "id": "a1234567-e89b-12d3-a456-426614174000",
     *   "audioRecordId": "550e8400-e29b-41d4-a716-446655440000",
     *   "content": "In today's meeting, we discussed the requirements...",
     *   "structuredData": {
     *     "event": "Requirements meeting with tech team",
     *     "duration_minutes": 40,
     *     "category": "meeting",
     *     "action_items": [
     *       "Review API specifications",
     *       "Schedule follow-up meeting"
     *     ]
     *   },
     *   "tokensUsed": 1250,
     *   "model": "anthropic/claude-3.5-sonnet",
     *   "createdAt": "2024-01-15T10:31:00"
     * }
     * </pre>
     *
     * Example error response (not found):
     * <pre>
     * {
     *   "type": "https://api.example.com/errors/not-found",
     *   "title": "Summary Not Found",
     *   "status": 404,
     *   "detail": "Summary not found: a1234567-e89b-12d3-a456-426614174000",
     *   "instance": "/api/summaries/a1234567-e89b-12d3-a456-426614174000"
     * }
     * </pre>
     *
     * Example error response (access denied):
     * <pre>
     * {
     *   "type": "https://api.example.com/errors/forbidden",
     *   "title": "Access Denied",
     *   "status": 403,
     *   "detail": "Access denied: summary does not belong to this user",
     *   "instance": "/api/summaries/a1234567-e89b-12d3-a456-426614174000"
     * }
     * </pre>
     */
    @GetMapping("/{id}")
    public ResponseEntity<SummaryResponse> getSummaryById(
            @PathVariable UUID id,
            Authentication authentication) {

        log.info("Summary requested: {} by user: {}", id, authentication.getName());

        try {
            SummaryResponse summary = summaryService.getSummaryById(id, authentication);

            log.info("Summary retrieved successfully: {} for user: {}",
                    id, authentication.getName());

            return ResponseEntity.ok(summary);

        } catch (IllegalArgumentException e) {
            log.warn("Summary not found or invalid request: {}", e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (SecurityException e) {
            log.warn("Access denied for summary: {} by user: {} - {}",
                    id, authentication.getName(), e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (Exception e) {
            log.error("Unexpected error retrieving summary: {} for user: {}",
                    id, authentication.getName(), e);
            throw e;  // GlobalExceptionHandler will handle this
        }
    }

    /**
     * Get summary for a specific audio record.
     *
     * This is a convenience endpoint to retrieve the summary for a specific
     * audio record. It returns the summary if processing is complete, or null
     * if the audio is still being processed.
     *
     * Endpoint: GET /api/summaries/by-audio/{audioRecordId}
     * Authentication: Required (JWT token)
     *
     * @param audioRecordId the audio record ID (UUID)
     * @param authentication the Spring Security authentication object (from JWT)
     * @return SummaryResponse if summary exists, null with 404 if not
     * @throws SecurityException if audio record doesn't belong to authenticated user
     *
     * Example request:
     * <pre>
     * GET /api/summaries/by-audio/550e8400-e29b-41d4-a716-446655440000
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     *
     * Example response (200 OK, summary exists):
     * <pre>
     * {
     *   "id": "a1234567-e89b-12d3-a456-426614174000",
     *   "audioRecordId": "550e8400-e29b-41d4-a716-446655440000",
     *   "content": "In today's meeting...",
     *   "structuredData": { ... },
     *   "tokensUsed": 1250,
     *   "model": "anthropic/claude-3.5-sonnet",
     *   "createdAt": "2024-01-15T10:31:00"
     * }
     * </pre>
     *
     * Example response (404 Not Found, summary not yet generated):
     * <pre>
     * {
     *   "type": "https://api.example.com/errors/not-found",
     *   "title": "Summary Not Found",
     *   "status": 404,
     *   "detail": "No summary found for audio record: 550e8400-e29b-41d4-a716-446655440000",
     *   "instance": "/api/summaries/by-audio/550e8400-e29b-41d4-a716-446655440000"
     * }
     * </pre>
     *
     * Note: This endpoint is useful for polling after audio upload to check
     * if processing has completed. For real-time updates, use the SSE endpoint
     * at /api/status/sse instead.
     */
    @GetMapping("/by-audio/{audioRecordId}")
    public ResponseEntity<SummaryResponse> getSummaryByAudioRecordId(
            @PathVariable UUID audioRecordId,
            Authentication authentication) {

        log.info("Summary requested for audio record: {} by user: {}",
                audioRecordId, authentication.getName());

        try {
            SummaryResponse summary = summaryService.getSummaryByAudioRecordId(
                    audioRecordId, authentication);

            if (summary == null) {
                log.info("No summary found for audio record: {} (processing may not be complete)",
                        audioRecordId);
                return ResponseEntity.notFound().build();
            }

            log.info("Summary retrieved successfully for audio record: {}",
                    audioRecordId);

            return ResponseEntity.ok(summary);

        } catch (SecurityException e) {
            log.warn("Access denied for audio record: {} by user: {} - {}",
                    audioRecordId, authentication.getName(), e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (Exception e) {
            log.error("Unexpected error retrieving summary for audio record: {} for user: {}",
                    audioRecordId, authentication.getName(), e);
            throw e;  // GlobalExceptionHandler will handle this
        }
    }

    /**
     * Get summary count for the authenticated user.
     *
     * This endpoint returns the total number of summaries for the authenticated
     * user. Useful for dashboard statistics and pagination metadata.
     *
     * Endpoint: GET /api/summaries/count
     * Authentication: Required (JWT token)
     *
     * @param authentication the Spring Security authentication object (from JWT)
     * @return Count of summaries for the user
     *
     * Example request:
     * <pre>
     * GET /api/summaries/count
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     *
     * Example response (200 OK):
     * <pre>
     * {
     *   "count": 45
     * }
     * </pre>
     */
    @GetMapping("/count")
    public ResponseEntity<CountResponse> getSummaryCount(Authentication authentication) {
        log.info("Summary count requested by user: {}", authentication.getName());

        try {
            long count = summaryService.countSummariesForUser(authentication);

            log.info("User {} has {} summaries", authentication.getName(), count);

            return ResponseEntity.ok(new CountResponse(count));

        } catch (Exception e) {
            log.error("Unexpected error counting summaries for user: {}",
                    authentication.getName(), e);
            throw e;  // GlobalExceptionHandler will handle this
        }
    }

    /**
     * Health check endpoint for summary service.
     *
     * This endpoint can be used to verify that the summary service
     * is operational. It requires authentication but returns a simple
     * success message.
     *
     * Endpoint: GET /api/summaries/health
     * Authentication: Required (JWT token)
     *
     * @param authentication the Spring Security authentication object
     * @return Health check response
     *
     * Example response:
     * <pre>
     * {
     *   "status": "healthy",
     *   "service": "summary-service"
     * }
     * </pre>
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> healthCheck(Authentication authentication) {
        return ResponseEntity.ok(new HealthResponse("healthy", "summary-service"));
    }

    /**
     * Response DTO for count endpoint.
     */
    public record CountResponse(long count) {}

    /**
     * Response DTO for health check endpoint.
     */
    public record HealthResponse(String status, String service) {}
}
