package com.prod.controller;

import com.prod.dto.request.AudioUploadRequest;
import com.prod.dto.response.AudioRecordResponse;
import com.prod.dto.response.ProcessingStatusResponse;
import com.prod.service.AudioProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for audio upload and management endpoints.
 *
 * This controller handles the audio upload flow and retrieval of audio records
 * for authenticated users. All endpoints require JWT authentication.
 *
 * Upload Flow:
 * 1. User uploads audio file to object storage (future: MinIO/Hetzner)
 * 2. User POSTs to /api/audio/upload with audio metadata
 * 3. System validates metadata, saves to database
 * 4. System creates ProcessingTask with PENDING status
 * 5. System sends audio record ID to RabbitMQ for async processing
 * 6. System returns immediately with audio record ID and task ID
 * 7. Consumer picks up task, processes via Whisper + Claude
 * 8. ProcessingTask status updates: PENDING → PROCESSING → COMPLETED/FAILED
 *
 * Security Features:
 * - JWT authentication required for all endpoints
 * - User isolation: Users can only access their own audio records
 * - Input validation: Jakarta validation annotations on DTOs
 * - File validation: Duration > 0, file size > 0, valid MIME type
 *
 * Error Responses:
 * - 400 Bad Request: Invalid input (validation errors)
 * - 401 Unauthorized: Missing or invalid JWT token
 * - 403 Forbidden: Attempting to access another user's records
 * - 503 Service Unavailable: RabbitMQ queue unavailable
 * - 500 Internal Server Error: Unexpected server errors
 *
 * All errors are handled by GlobalExceptionHandler and returned in RFC 7807 format.
 *
 * @see com.prod.service.AudioProcessingService
 * @see com.prod.messaging.AudioProcessingConsumer
 * @see com.prod.config.SecurityConfig
 */
@RestController
@RequestMapping("/api/audio")
@RequiredArgsConstructor
@Slf4j
public class AudioController {

    private final AudioProcessingService audioProcessingService;

    /**
     * Upload audio metadata and initiate processing.
     *
     * This endpoint accepts audio file metadata and initiates the asynchronous
     * processing pipeline. The actual audio file should be uploaded to object
     * storage (MinIO/Hetzner) before calling this endpoint.
     *
     * The endpoint returns immediately after saving the metadata and queuing
     * the processing task. The actual transcription and analysis happen
     * asynchronously in the background via RabbitMQ.
     *
     * Endpoint: POST /api/audio/upload
     * Authentication: Required (JWT token)
     *
     * @param uploadRequest the audio upload request containing metadata
     * @param authentication the Spring Security authentication object (from JWT)
     * @return AudioRecordResponse with audio record ID and processing status
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if queue operation fails
     *
     * Example request:
     * <pre>
     * POST /api/audio/upload
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * Content-Type: application/json
     *
     * {
     *   "fileKey": "audio/2024/01/15/recording-abc123.mp3",
     *   "fileName": "meeting-recording.mp3",
     *   "durationSeconds": 120,
     *   "mimeType": "audio/mpeg",
     *   "fileSizeBytes": 2400000
     * }
     * </pre>
     *
     * Example response (201 Created):
     * <pre>
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440000",
     *   "fileName": "meeting-recording.mp3",
     *   "fileKey": "audio/2024/01/15/recording-abc123.mp3",
     *   "durationSeconds": 120,
     *   "mimeType": "audio/mpeg",
     *   "fileSizeBytes": 2400000,
     *   "createdAt": "2024-01-15T10:30:00",
     *   "updatedAt": "2024-01-15T10:30:00",
     *   "processingStatus": "PENDING",
     *   "summaryId": null,
     *   "errorMessage": null
     * }
     * </pre>
     *
     * Example error response (validation failed):
     * <pre>
     * {
     *   "type": "https://api.example.com/errors/validation",
     *   "title": "Validation Failed",
     *   "status": 400,
     *   "detail": "Duration seconds must be greater than 0",
     *   "instance": "/api/audio/upload"
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
     *   "instance": "/api/audio/upload"
     * }
     * </pre>
     *
     * Processing Flow:
     * 1. Validate JWT token and extract user ID
     * 2. Validate request parameters (fileKey, fileName, duration, etc.)
     * 3. Create AudioRecord entity with metadata
     * 4. Create ProcessingTask entity with PENDING status
     * 5. Send audio record ID to RabbitMQ queue
     * 6. Return audio record details immediately
     *
     * Expected Response Time: < 1 second (non-blocking)
     *
     * Note: The audio file itself must be uploaded to object storage before
     * calling this endpoint. This endpoint only handles the metadata and
     * initiates the processing pipeline.
     */
    @PostMapping("/upload")
    public ResponseEntity<AudioRecordResponse> uploadAudio(
            @Valid @RequestBody AudioUploadRequest uploadRequest,
            Authentication authentication) {

        log.info("Audio upload request received from user: {}, fileName: {}",
                authentication != null ? authentication.getName() : "anonymous",
                uploadRequest.getFileName());

        try {
            // Process upload through service layer
            AudioProcessingService.AudioUploadResponse response = audioProcessingService.processUpload(
                    authentication,
                    uploadRequest.getFileKey(),
                    uploadRequest.getFileName(),
                    uploadRequest.getDurationSeconds(),
                    uploadRequest.getMimeType(),
                    uploadRequest.getFileSizeBytes()
            );

            log.info("Audio uploaded successfully: audioRecordId={}, taskId={}",
                    response.getAudioRecordId(), response.getProcessingTaskId());

            // Build response DTO
            AudioRecordResponse audioRecordResponse = AudioRecordResponse.builder()
                    .id(response.getAudioRecordId())
                    .fileName(uploadRequest.getFileName())
                    .fileKey(uploadRequest.getFileKey())
                    .durationSeconds(uploadRequest.getDurationSeconds())
                    .mimeType(uploadRequest.getMimeType())
                    .fileSizeBytes(uploadRequest.getFileSizeBytes())
                    .processingStatus(response.getStatus())
                    .build();

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(audioRecordResponse);

        } catch (IllegalArgumentException e) {
            log.warn("Audio upload validation failed: {}", e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (IllegalStateException e) {
            log.error("Audio upload service unavailable: {}", e.getMessage());
            throw e;  // GlobalExceptionHandler will handle this

        } catch (Exception e) {
            log.error("Unexpected error during audio upload: fileName={}",
                    uploadRequest.getFileName(), e);
            throw e;  // GlobalExceptionHandler will handle this
        }
    }

    /**
     * Get processing status for an audio record.
     *
     * This endpoint returns the current processing status for a specific audio record.
     * Clients can poll this endpoint to check if processing is complete.
     *
     * Endpoint: GET /api/audio/{id}/status
     * Authentication: Required (JWT token)
     *
     * @param id the audio record ID
     * @param authentication the Spring Security authentication object
     * @return ProcessingStatusResponse with current status
     *
     * Example request:
     * <pre>
     * GET /api/audio/550e8400-e29b-41d4-a716-446655440000/status
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     *
     * Example response:
     * <pre>
     * {
     *   "taskId": "987fcdeb-51a2-43f1-a456-426614174999",
     *   "audioRecordId": "550e8400-e29b-41d4-a716-446655440000",
     *   "status": "PROCESSING",
     *   "createdAt": "2024-01-15T10:30:00",
     *   "startedAt": "2024-01-15T10:30:05",
     *   "completedAt": null,
     *   "retryCount": 0,
     *   "errorLog": null,
     *   "estimatedSecondsRemaining": 30
     * }
     * </pre>
     *
     * Note: For real-time updates, clients should use the SSE endpoint at
     * /api/status/sse instead of polling this endpoint.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<ProcessingStatusResponse> getProcessingStatus(
            @PathVariable UUID id,
            Authentication authentication) {

        log.info("Processing status requested for audio record: {} by user: {}",
                id, authentication.getName());

        // TODO: Implement status retrieval from ProcessingTaskRepository
        // This will be implemented in a separate subtask
        ProcessingStatusResponse response = ProcessingStatusResponse.builder()
                .audioRecordId(id)
                .status("NOT_IMPLEMENTED")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get details of a specific audio record.
     *
     * This endpoint returns the complete details of an audio record including
     * its processing status and summary ID (if available).
     *
     * Endpoint: GET /api/audio/{id}
     * Authentication: Required (JWT token)
     *
     * @param id the audio record ID
     * @param authentication the Spring Security authentication object
     * @return AudioRecordResponse with complete record details
     *
     * Example request:
     * <pre>
     * GET /api/audio/550e8400-e29b-41d4-a716-446655440000
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     *
     * Example response:
     * <pre>
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440000",
     *   "fileName": "meeting-recording.mp3",
     *   "fileKey": "audio/2024/01/15/recording-abc123.mp3",
     *   "durationSeconds": 120,
     *   "mimeType": "audio/mpeg",
     *   "fileSizeBytes": 2400000,
     *   "createdAt": "2024-01-15T10:30:00",
     *   "updatedAt": "2024-01-15T10:31:30",
     *   "processingStatus": "COMPLETED",
     *   "summaryId": "a1234567-e89b-12d3-a456-426614174000",
     *   "errorMessage": null
     * }
     * </pre>
     *
     * Note: This endpoint will be fully implemented in a separate subtask
     * with user isolation logic (users can only access their own records).
     */
    @GetMapping("/{id}")
    public ResponseEntity<AudioRecordResponse> getAudioRecord(
            @PathVariable UUID id,
            Authentication authentication) {

        log.info("Audio record requested: {} by user: {}",
                id, authentication.getName());

        // TODO: Implement record retrieval from AudioRecordRepository
        // This will be implemented in a separate subtask
        AudioRecordResponse response = AudioRecordResponse.builder()
                .id(id)
                .processingStatus("NOT_IMPLEMENTED")
                .build();

        return ResponseEntity.ok(response);
    }
}
