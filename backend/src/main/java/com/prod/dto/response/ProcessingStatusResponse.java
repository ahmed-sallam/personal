package com.prod.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for processing task status.
 *
 * This DTO is returned when clients need to check the status of an audio
 * processing task. It provides real-time information about where the task
 * is in the processing pipeline.
 *
 * Use Cases:
 * 1. After audio upload - Returns initial status (PENDING)
 * 2. SSE events - Emits status updates as processing progresses
 * 3. GET /api/audio/{id}/status - Returns current status on demand
 *
 * Processing Flow:
 * PENDING → PROCESSING → COMPLETED or FAILED
 *
 * Example JSON response:
 * <pre>
 * {
 *   "taskId": "987fcdeb-51a2-43f1-a456-426614174999",
 *   "audioRecordId": "550e8400-e29b-41d4-a716-446655440000",
 *   "status": "PROCESSING",
 *   "createdAt": "2024-01-15T10:30:00",
 *   "startedAt": "2024-01-15T10:30:05",
 *   "completedAt": null,
 *   "retryCount": 0,
 *   "errorLog": null
 * }
 * </pre>
 *
 * Example response for failed task:
 * <pre>
 * {
 *   "taskId": "987fcdeb-51a2-43f1-a456-426614174999",
 *   "audioRecordId": "550e8400-e29b-41d4-a716-446655440000",
 *   "status": "FAILED",
 *   "createdAt": "2024-01-15T10:30:00",
 *   "startedAt": "2024-01-15T10:30:05",
 *   "completedAt": "2024-01-15T10:31:00",
 *   "retryCount": 3,
 *   "errorLog": "Whisper API timeout after 3 attempts"
 * }
 * </pre>
 *
 * Status Values:
 * - PENDING: Task is queued, waiting for a consumer to pick it up
 * - PROCESSING: Consumer is actively processing (Whisper transcription + Claude analysis)
 * - COMPLETED: Task finished successfully, summary is available
 * - FAILED: Task failed after all retry attempts, check errorLog
 *
 * @see com.prod.entity.ProcessingTask
 * @see com.prod.dto.response.AudioRecordResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingStatusResponse {

    /**
     * Unique identifier of the processing task.
     *
     * UUID format. Used to track and query the processing task status.
     */
    private UUID taskId;

    /**
     * ID of the audio record being processed.
     *
     * Links this processing task to the original audio upload.
     * Can be used to retrieve the audio record details.
     */
    private UUID audioRecordId;

    /**
     * Current processing status.
     *
     * Indicates the current state of the task:
     * - PENDING: Queued, waiting to be picked up by a consumer
     * - PROCESSING: actively being processed (transcription + analysis)
     * - COMPLETED: Processing finished successfully
     * - FAILED: Processing failed after retry attempts
     */
    private String status;

    /**
     * Timestamp when the task was created.
     *
     * Set when the audio is uploaded and the task is first created.
     * This is the time the task entered the queue.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when processing started.
     *
     * Set when a consumer picks up the task and marks it as PROCESSING.
     * Used to calculate queue wait time and processing duration.
     */
    private LocalDateTime startedAt;

    /**
     * Timestamp when processing completed.
     *
     * Set when the task finishes (either COMPLETED or FAILED).
     * Used to calculate total processing time.
     */
    private LocalDateTime completedAt;

    /**
     * Number of retry attempts made.
     *
     * Incremented each time the task fails and is re-queued.
     * Maximum 3 retries are allowed before the task is marked as FAILED.
     */
    private Integer retryCount;

    /**
     * Error message if the task failed.
     *
     * Populated when status is FAILED.
     * Contains details about what went wrong during processing.
     * May include stack traces for debugging purposes.
     */
    private String errorLog;

    /**
     * Estimated time remaining for processing (in seconds).
     *
     * Optional field that provides an estimate of how long processing
     * will take. Only populated when status is PROCESSING.
     * Calculated based on audio duration and historical processing times.
     */
    private Integer estimatedSecondsRemaining;
}
