package com.prod.service;

import com.prod.entity.AudioRecord;
import com.prod.entity.ProcessingTask;
import com.prod.entity.User;
import com.prod.messaging.AudioProcessingProducer;
import com.prod.repository.AudioRecordRepository;
import com.prod.repository.ProcessingTaskRepository;
import com.prod.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for handling audio upload orchestration and asynchronous processing.
 *
 * This service manages the complete flow for audio file uploads in the productivity
 * tracking system. It coordinates between multiple components to ensure audio files
 * are properly stored, tracked, and processed asynchronously through the AI pipeline.
 *
 * Upload Flow:
 * 1. User uploads audio file with metadata (via AudioController)
 * 2. Service validates metadata and extracts authenticated user
 * 3. AudioRecord entity is saved to database with file metadata
 * 4. ProcessingTask entity is created with PENDING status
 * 5. Audio record ID is sent to RabbitMQ queue for async processing
 * 6. Response returned immediately to user (non-blocking)
 * 7. AudioProcessingConsumer picks up task and processes through AI pipeline
 * 8. ProcessingTask status updated: PENDING → PROCESSING → COMPLETED/FAILED
 *
 * Benefits of Async Processing:
 * - Non-blocking upload: HTTP request returns in < 1 second
 * - Scalability: Multiple consumers can process in parallel
 * - Resilience: Failed tasks can be retried automatically
 * - Monitoring: Real-time status updates via SSE endpoint
 *
 * Data Flow:
 * - AudioController → AudioProcessingService → Repositories (save)
 * - AudioProcessingService → AudioProcessingProducer → RabbitMQ
 * - RabbitMQ → AudioProcessingConsumer → AI Services (Whisper, Claude)
 * - AudioProcessingConsumer → Repositories (update status)
 *
 * Security:
 * - User authentication extracted from JWT token
 * - Audio records are isolated by user (user_id foreign key)
 * - Users can only access their own audio records
 *
 * Error Handling:
 * - Validation errors: Return 400 Bad Request
 * - Database errors: Return 500 Internal Server Error
 * - Queue errors: Return 503 Service Unavailable
 * - Processing errors: Logged in ProcessingTask.error_log
 *
 * @see com.prod.messaging.AudioProcessingProducer
 * @see com.prod.messaging.AudioProcessingConsumer
 * @see com.prod.entity.AudioRecord
 * @see com.prod.entity.ProcessingTask
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AudioProcessingService {

    private final AudioRecordRepository audioRecordRepository;
    private final ProcessingTaskRepository processingTaskRepository;
    private final UserRepository userRepository;
    private final AudioProcessingProducer audioProcessingProducer;

    /**
     * Process audio upload and orchestrate asynchronous processing.
     *
     * This method handles the complete upload flow for audio files. It validates
     * input, saves metadata to the database, creates a processing task, and sends
     * the task to the RabbitMQ queue for asynchronous AI processing.
     *
     * Processing Steps:
     * 1. Extract authenticated user from security context
     * 2. Validate all input parameters
     * 3. Create and save AudioRecord entity with metadata
     * 4. Create and save ProcessingTask entity with PENDING status
     * 5. Send audio record ID to RabbitMQ queue
     * 6. Return response immediately (async processing continues in background)
     *
     * Transaction Management:
     * - Entire operation is wrapped in a single transaction
     * - If any step fails, all database changes are rolled back
     * - This ensures data consistency (no orphaned records)
     *
     * Input Validation:
     * - User must be authenticated (non-null authentication)
     * - User must exist in database
     * - fileKey must not be null or empty
     * - fileName must not be null or empty
     * - durationSeconds must be greater than 0
     * - mimeType must not be null or empty
     * - fileSizeBytes must be greater than 0
     *
     * Expected Response Time: < 1 second (non-blocking)
     *
     * @param authentication the Spring Security authentication object (contains JWT token)
     * @param fileKey the object storage key/path for the audio file
     * @param fileName the original filename as uploaded by the user
     * @param durationSeconds the audio duration in seconds (must be > 0)
     * @param mimeType the MIME type of the audio file (e.g., "audio/mpeg", "audio/wav")
     * @param fileSizeBytes the file size in bytes (must be > 0)
     * @return AudioUploadResponse containing audio record ID and processing task ID
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if user not found or queue operation fails
     */
    @Transactional
    public AudioUploadResponse processUpload(
            Authentication authentication,
            String fileKey,
            String fileName,
            Integer durationSeconds,
            String mimeType,
            Long fileSizeBytes) {

        // Step 1: Extract authenticated user
        User user = extractAuthenticatedUser(authentication);
        log.info("Processing audio upload request for user: {}, fileName: {}",
                user.getEmail(), fileName);

        // Step 2: Validate input parameters
        validateUploadRequest(fileKey, fileName, durationSeconds, mimeType, fileSizeBytes);

        // Step 3: Create and save AudioRecord
        log.debug("Creating audio record: user={}, fileKey={}, fileName={}",
                user.getId(), fileKey, fileName);
        AudioRecord audioRecord = createAudioRecord(
                user, fileKey, fileName, durationSeconds, mimeType, fileSizeBytes);
        audioRecord = audioRecordRepository.save(audioRecord);
        log.info("Audio record saved: id={}, user={}, fileName={}",
                audioRecord.getId(), user.getId(), fileName);

        // Step 4: Create and save ProcessingTask
        log.debug("Creating processing task: audioRecordId={}", audioRecord.getId());
        ProcessingTask processingTask = new ProcessingTask(audioRecord);
        processingTask = processingTaskRepository.save(processingTask);
        log.info("Processing task created: id={}, audioRecordId={}, status={}",
                processingTask.getId(), audioRecord.getId(), processingTask.getStatus());

        // Step 5: Send to RabbitMQ queue for async processing
        try {
            log.debug("Sending audio processing task to queue: audioRecordId={}",
                    audioRecord.getId());
            audioProcessingProducer.sendProcessingTask(audioRecord.getId());
            log.info("Audio processing task queued successfully: audioRecordId={}, taskId={}",
                    audioRecord.getId(), processingTask.getId());

        } catch (Exception e) {
            // Queue operation failed - this is a service availability issue
            log.error("Failed to send audio processing task to queue: audioRecordId={}, error={}",
                    audioRecord.getId(), e.getMessage(), e);
            throw new IllegalStateException(
                    "Audio upload queued but processing service unavailable. Please try again later.",
                    e);
        }

        // Step 6: Return response immediately (async processing continues)
        log.info("Audio upload completed successfully: audioRecordId={}, taskId={}, user={}",
                audioRecord.getId(), processingTask.getId(), user.getEmail());

        return new AudioUploadResponse(
                audioRecord.getId(),
                processingTask.getId(),
                processingTask.getStatus().name(),
                "Audio uploaded successfully. Processing started."
        );
    }

    /**
     * Extract authenticated user from Spring Security authentication context.
     *
     * This method retrieves the user ID from the JWT token and loads the full
     * User entity from the database. The JWT token contains the user ID in the
     * subject claim, which is set during authentication.
     *
     * @param authentication the Spring Security authentication object
     * @return the authenticated User entity
     * @throws IllegalArgumentException if authentication is null or user not found
     */
    private User extractAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Attempted to process audio upload without authentication");
            throw new IllegalArgumentException("User must be authenticated to upload audio");
        }

        // Extract user ID from authentication principal
        // The JwtAuthenticationFilter sets the principal as the User ID
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in authentication principal: {}", authentication.getName());
            throw new IllegalArgumentException("Invalid user authentication");
        }

        // Load user from database
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found in database during audio upload: userId={}", userId);
                    return new IllegalArgumentException("Authenticated user not found");
                });
    }

    /**
     * Validate audio upload request parameters.
     *
     * This method performs comprehensive validation of all input parameters
     * to ensure data integrity and prevent invalid records from being created.
     *
     * Validation Rules:
     * - fileKey: Required, non-empty string
     * - fileName: Required, non-empty string
     * - durationSeconds: Required, > 0 (no zero-duration or negative audio)
     * - mimeType: Required, must start with "audio/"
     * - fileSizeBytes: Required, > 0 (no empty files)
     *
     * @param fileKey the object storage key
     * @param fileName the original filename
     * @param durationSeconds the audio duration in seconds
     * @param mimeType the MIME type
     * @param fileSizeBytes the file size in bytes
     * @throws IllegalArgumentException if any validation fails
     */
    private void validateUploadRequest(
            String fileKey,
            String fileName,
            Integer durationSeconds,
            String mimeType,
            Long fileSizeBytes) {

        log.debug("Validating audio upload request: fileName={}, durationSeconds={}, mimeType={}",
                fileName, durationSeconds, mimeType);

        // Validate fileKey
        if (fileKey == null || fileKey.trim().isEmpty()) {
            log.warn("Audio upload validation failed: fileKey is null or empty");
            throw new IllegalArgumentException("File key cannot be null or empty");
        }

        // Validate fileName
        if (fileName == null || fileName.trim().isEmpty()) {
            log.warn("Audio upload validation failed: fileName is null or empty");
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        // Validate durationSeconds
        if (durationSeconds == null) {
            log.warn("Audio upload validation failed: durationSeconds is null");
            throw new IllegalArgumentException("Duration seconds cannot be null");
        }
        if (durationSeconds <= 0) {
            log.warn("Audio upload validation failed: durationSeconds is invalid: {}",
                    durationSeconds);
            throw new IllegalArgumentException("Duration seconds must be greater than 0");
        }

        // Validate mimeType
        if (mimeType == null || mimeType.trim().isEmpty()) {
            log.warn("Audio upload validation failed: mimeType is null or empty");
            throw new IllegalArgumentException("MIME type cannot be null or empty");
        }
        if (!mimeType.startsWith("audio/")) {
            log.warn("Audio upload validation failed: invalid MIME type: {}", mimeType);
            throw new IllegalArgumentException("MIME type must be a valid audio type (e.g., audio/mpeg, audio/wav)");
        }

        // Validate fileSizeBytes
        if (fileSizeBytes == null) {
            log.warn("Audio upload validation failed: fileSizeBytes is null");
            throw new IllegalArgumentException("File size bytes cannot be null");
        }
        if (fileSizeBytes <= 0) {
            log.warn("Audio upload validation failed: fileSizeBytes is invalid: {}",
                    fileSizeBytes);
            throw new IllegalArgumentException("File size bytes must be greater than 0");
        }

        log.debug("Audio upload request validation passed");
    }

    /**
     * Create a new AudioRecord entity with the provided metadata.
     *
     * This factory method creates an AudioRecord instance using the entity's
     * constructor, ensuring all required fields are properly initialized.
     *
     * @param user the user who uploaded the audio
     * @param fileKey the object storage key
     * @param fileName the original filename
     * @param durationSeconds the audio duration
     * @param mimeType the MIME type
     * @param fileSizeBytes the file size
     * @return a new AudioRecord entity
     */
    private AudioRecord createAudioRecord(
            User user,
            String fileKey,
            String fileName,
            Integer durationSeconds,
            String mimeType,
            Long fileSizeBytes) {

        return new AudioRecord(user, fileKey, fileName, durationSeconds, mimeType, fileSizeBytes);
    }

    /**
     * Response DTO for audio upload operations.
     *
     * This internal class represents the response returned to clients after
     * a successful audio upload. It contains the IDs needed to track the
     * audio record and its processing status.
     *
     * In the API layer (AudioController), this will be converted to a JSON response.
     *
     * Example JSON response:
     * <pre>
     * {
     *   "audioRecordId": "123e4567-e89b-12d3-a456-426614174000",
     *   "processingTaskId": "987fcdeb-51a2-43f1-a456-426614174999",
     *   "status": "PENDING",
     *   "message": "Audio uploaded successfully. Processing started."
     * }
     * </pre>
     */
    public static class AudioUploadResponse {
        /**
         * The UUID of the created audio record.
         * Used to retrieve the audio record and its summary later.
         */
        private final UUID audioRecordId;

        /**
         * The UUID of the processing task.
         * Used to track the processing status via SSE or status endpoint.
         */
        private final UUID processingTaskId;

        /**
         * The current processing status.
         * Initially "PENDING", will transition to "PROCESSING" → "COMPLETED" or "FAILED".
         */
        private final String status;

        /**
         * Human-readable message about the upload.
         */
        private final String message;

        /**
         * Constructor for AudioUploadResponse.
         *
         * @param audioRecordId the audio record UUID
         * @param processingTaskId the processing task UUID
         * @param status the processing status
         * @param message the response message
         */
        public AudioUploadResponse(UUID audioRecordId, UUID processingTaskId,
                                   String status, String message) {
            this.audioRecordId = audioRecordId;
            this.processingTaskId = processingTaskId;
            this.status = status;
            this.message = message;
        }

        public UUID getAudioRecordId() {
            return audioRecordId;
        }

        public UUID getProcessingTaskId() {
            return processingTaskId;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}
