package com.prod.controller;

import com.prod.entity.ProcessingTask;
import com.prod.entity.ProcessingTask.ProcessingStatus;
import com.prod.repository.ProcessingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * REST Controller for real-time status updates via Server-Sent Events (SSE).
 *
 * This controller provides an SSE endpoint that clients can connect to for receiving
 * real-time updates on audio processing status. As processing tasks progress through
 * the pipeline (PENDING → PROCESSING → COMPLETED/FAILED), status events are pushed
 * to connected clients.
 *
 * SSE Flow:
 * 1. Client connects to GET /api/status/sse with valid JWT token
 * 2. Server establishes SSE connection and keeps it open
 * 3. Server queries database for user's processing tasks at regular intervals
 * 4. When status changes are detected, server sends SSE events to client
 * 5. Connection remains open until client disconnects or timeout occurs
 *
 * Security Features:
 * - JWT authentication required for SSE connection
 * - User isolation: Each client receives only their own status updates
 * - Connection timeout: 30 minutes to prevent resource leaks
 * - Automatic cleanup: Disconnected clients are removed from tracking
 *
 * SSE Event Format:
 * <pre>
 * event: status-update
 * data: {
 *   "audioRecordId": "550e8400-e29b-41d4-a716-446655440000",
 *   "status": "PROCESSING",
 *   "taskId": "987fcdeb-51a2-43f1-a456-426614174999",
 *   "timestamp": "2024-01-15T10:30:05"
 * }
 * </pre>
 *
 * Error Responses:
 * - 401 Unauthorized: Missing or invalid JWT token
 * - 500 Internal Server Error: SSE connection establishment failed
 * - 503 Service Unavailable: Database connection issues
 *
 * Client-Side Usage:
 * <pre>
 * const eventSource = new EventSource('/api/status/sse', {
 *   headers: { 'Authorization': 'Bearer ' + token }
 * });
 * eventSource.addEventListener('status-update', (event) => {
 *   const data = JSON.parse(event.data);
 *   console.log('Status update:', data.status);
 * });
 * eventSource.onerror = (error) => {
 *   console.error('SSE connection error:', error);
 *   eventSource.close();
 * };
 * </pre>
 *
 * @see com.prod.repository.ProcessingTaskRepository
 * @see com.prod.messaging.AudioProcessingConsumer
 * @see com.prod.config.SecurityConfig
 */
@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
@Slf4j
public class StatusController {

    private final ProcessingTaskRepository processingTaskRepository;

    /**
     * Timeout for SSE connections in milliseconds.
     * 30 minutes to balance between keeping clients updated and preventing resource leaks.
     */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * Polling interval in milliseconds for checking status updates.
     * 2 seconds provides responsive updates without overwhelming the database.
     */
    private static final long POLLING_INTERVAL_MS = 2000L;

    /**
     * In-memory storage of active SSE emitters keyed by user ID.
     * Thread-safe map for concurrent access from multiple requests.
     */
    private final Map<UUID, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /**
     * In-memory tracking of last known status for each task to detect changes.
     * Outer map: user ID → Inner map: audio record ID → last known status
     */
    private final Map<UUID, Map<UUID, ProcessingStatus>> lastKnownStatus = new ConcurrentHashMap<>();

    /**
     * Scheduled executor service for periodic status polling.
     * Single thread for all SSE connections to avoid overwhelming the database.
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "sse-status-poller");
        thread.setDaemon(true);  // Don't prevent JVM shutdown
        return thread;
    });

    /**
     * Establish SSE connection for real-time status updates.
     *
     * This endpoint creates a long-lived SSE connection that pushes status updates
     * for the authenticated user's processing tasks. The connection remains open
     * for up to 30 minutes, sending events whenever processing status changes.
     *
     * The endpoint uses polling to check for status changes at 2-second intervals.
     * For each check, it queries the user's processing tasks and compares the current
     * status with the last known status. When a change is detected, an SSE event
     * is sent to the client.
     *
     * Endpoint: GET /api/status/sse
     * Authentication: Required (JWT token)
     * Response Type: text/event-stream (SSE)
     *
     * @param authentication the Spring Security authentication object (from JWT)
     * @return SseEmitter for streaming status updates
     *
     * Example request:
     * <pre>
     * GET /api/status/sse
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * Accept: text/event-stream
     * </pre>
     *
     * Example SSE events stream:
     * <pre>
     * event: status-update
     * data: {"audioRecordId":"550e8400-e29b-41d4-a716-446655440000","status":"PROCESSING","taskId":"987fcdeb-51a2-43f1-a456-426614174999","timestamp":"2024-01-15T10:30:05"}
     *
     * event: status-update
     * data: {"audioRecordId":"550e8400-e29b-41d4-a716-446655440000","status":"COMPLETED","taskId":"987fcdeb-51a2-43f1-a456-426614174999","timestamp":"2024-01-15T10:31:30"}
     * </pre>
     *
     * Error Handling:
     * - If JWT is missing/invalid, returns 401 Unauthorized before SSE connection
     * - If client disconnects, emitter is cleaned up automatically
     * - If database query fails, logs error and continues polling
     *
     * Performance Considerations:
     * - Single shared scheduler for all clients to limit database load
     * - Polling at 2-second intervals balances responsiveness and efficiency
     * - For production with many clients, consider using WebSocket or Redis Pub/Sub
     *
     * Future Enhancements:
     * - Use Redis Pub/Sub for instant status updates instead of polling
     * - Add heartbeat events to detect stale connections
     * - Implement filtering by audio record ID for targeted updates
     * - Add retry mechanism for failed SSE event delivery
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatusUpdates(Authentication authentication) {
        // Extract user ID from JWT authentication
        UUID userId = UUID.fromString(authentication.getName());

        log.info("SSE connection requested by user: {}", userId);

        // Create SSE emitter with timeout
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // Store emitter for this user
        activeEmitters.put(userId, emitter);
        lastKnownStatus.putIfAbsent(userId, new ConcurrentHashMap<>());

        // Initialize emitter completion and error handlers for cleanup
        emitter.onCompletion(() -> {
            log.info("SSE connection completed for user: {}", userId);
            cleanupEmitter(userId);
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timed out for user: {}", userId);
            cleanupEmitter(userId);
        });

        emitter.onError((ex) -> {
            log.error("SSE connection error for user: {}", userId, ex);
            cleanupEmitter(userId);
        });

        try {
            // Send initial connection event
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "message", "SSE connection established",
                            "userId", userId.toString(),
                            "timestamp", LocalDateTime.now().toString()
                    )));

            // Start periodic status polling for this user
            startStatusPolling(userId, emitter);

            log.info("SSE connection established successfully for user: {}", userId);

        } catch (IOException e) {
            log.error("Failed to establish SSE connection for user: {}", userId, e);
            cleanupEmitter(userId);
            throw new RuntimeException("Failed to establish SSE connection", e);
        }

        return emitter;
    }

    /**
     * Start periodic polling for status updates for a specific user.
     *
     * This method schedules a recurring task that queries the database for
     * the user's processing tasks and sends SSE events when status changes
     * are detected.
     *
     * @param userId the user ID to poll for
     * @param emitter the SSE emitter to send events to
     */
    private void startStatusPolling(UUID userId, SseEmitter emitter) {
        Runnable pollingTask = () -> {
            try {
                // Check if emitter is still active
                if (!activeEmitters.containsKey(userId)) {
                    log.debug("Emitter no longer active for user: {}, stopping polling", userId);
                    return;
                }

                // Get all processing tasks for this user
                List<ProcessingTask> tasks = processingTaskRepository
                        .findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.Pageable.unpaged())
                        .getContent();

                Map<UUID, ProcessingStatus> userLastKnownStatus = lastKnownStatus.get(userId);

                // Check for status changes
                for (ProcessingTask task : tasks) {
                    UUID audioRecordId = task.getAudioRecord().getId();
                    ProcessingStatus currentStatus = task.getStatus();
                    ProcessingStatus lastStatus = userLastKnownStatus.get(audioRecordId);

                    // If status changed or is new, send update
                    if (lastStatus == null || !lastStatus.equals(currentStatus)) {
                        sendStatusUpdate(emitter, task, userId);
                        userLastKnownStatus.put(audioRecordId, currentStatus);
                    }
                }

            } catch (Exception e) {
                log.error("Error during status polling for user: {}", userId, e);
                // Don't remove emitter on polling error, keep trying
            }
        };

        // Schedule periodic polling
        scheduler.scheduleAtFixedRate(pollingTask, 0, POLLING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Send a status update event to the SSE emitter.
     *
     * @param emitter the SSE emitter to send to
     * @param task the processing task with updated status
     * @param userId the user ID for logging
     */
    private void sendStatusUpdate(SseEmitter emitter, ProcessingTask task, UUID userId) {
        try {
            Map<String, Object> statusData = Map.of(
                    "audioRecordId", task.getAudioRecord().getId().toString(),
                    "status", task.getStatus().name(),
                    "taskId", task.getId().toString(),
                    "timestamp", LocalDateTime.now().toString()
            );

            emitter.send(SseEmitter.event()
                    .name("status-update")
                    .data(statusData));

            log.debug("Status update sent for user: {}, audioRecordId: {}, status: {}",
                    userId, task.getAudioRecord().getId(), task.getStatus());

        } catch (IOException e) {
            log.error("Failed to send status update for user: {}", userId, e);
            // Emitter will be cleaned up by error handler
        }
    }

    /**
     * Clean up resources for a disconnected emitter.
     *
     * This method removes the emitter from the active emitters map and
     * clears the last known status tracking for the user.
     *
     * @param userId the user ID to clean up
     */
    private void cleanupEmitter(UUID userId) {
        activeEmitters.remove(userId);
        lastKnownStatus.remove(userId);
        log.info("Cleaned up SSE resources for user: {}", userId);
    }

    /**
     * Get count of active SSE connections.
     *
     * This method can be used for monitoring and debugging purposes.
     *
     * @return number of active SSE connections
     */
    @GetMapping("/sse/connections")
    public Map<String, Integer> getActiveConnections() {
        return Map.of("activeConnections", activeEmitters.size());
    }
}
