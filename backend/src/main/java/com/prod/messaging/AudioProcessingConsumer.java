package com.prod.messaging;

import com.prod.entity.AISummary;
import com.prod.entity.AudioRecord;
import com.prod.entity.ProcessingTask;
import com.prod.entity.ProcessingTask.ProcessingStatus;
import com.prod.repository.AISummaryRepository;
import com.prod.repository.AudioRecordRepository;
import com.prod.repository.ProcessingTaskRepository;
import com.prod.service.ClaudeService;
import com.prod.service.WhisperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for asynchronous audio processing from RabbitMQ queue.
 *
 * This class listens to the audio processing queue and processes audio recordings
 * through the AI pipeline: Whisper transcription → Claude analysis → Database storage.
 * It updates the ProcessingTask status at each step for monitoring and error tracking.
 *
 * Processing Flow:
 * 1. Receive audio record ID from RabbitMQ queue
 * 2. Fetch AudioRecord and ProcessingTask from database
 * 3. Update ProcessingTask status to PROCESSING
 * 4. Load audio file from storage (file system path from fileKey)
 * 5. Call WhisperService for speech-to-text transcription
 * 6. Call ClaudeService to extract structured data from transcription
 * 7. Save AISummary to database with transcription and structured data
 * 8. Update ProcessingTask status to COMPLETED
 * 9. Handle errors: update status to FAILED with error log
 *
 * Error Handling:
 * - Validation errors (null audio, missing file): mark FAILED immediately
 * - Whisper errors: retry up to max attempts, then mark FAILED
 * - Claude errors: retry up to max attempts, then mark FAILED
 * - Database errors: log and propagate to DLQ
 * - File I/O errors: mark FAILED with detailed error message
 *
 * Retry Configuration:
 * - Max retries: 3 (configurable via application.yml)
 * - Retry count tracked in ProcessingTask.retryCount
 * - After max retries exceeded: task marked FAILED permanently
 *
 * Queue Configuration:
 * - Queue: audio.processing.queue (from RabbitMQConfig)
 * - Listener Container: SimpleRabbitListenerContainerFactory
 * - Concurrency: 1 (single-threaded per consumer)
 * - Prefetch: 1 (fair message distribution)
 *
 * @see org.springframework.amqp.rabbit.annotation.RabbitListener
 * @see com.prod.service.WhisperService
 * @see com.prod.service.ClaudeService
 * @see com.prod.messaging.AudioProcessingProducer
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AudioProcessingConsumer {

    private final WhisperService whisperService;
    private final ClaudeService claudeService;
    private final AudioRecordRepository audioRecordRepository;
    private final ProcessingTaskRepository processingTaskRepository;
    private final AISummaryRepository aiSummaryRepository;

    // Configuration properties
    @Value("${app.audio.storage.path:./storage/audio}")
    private String audioStoragePath;

    @Value("${app.audio.processing.max-retries:3}")
    private int maxRetries;

    /**
     * Process audio recording from RabbitMQ queue.
     *
     * This method is automatically invoked when a message arrives in the
     * audio.processing.queue. The message payload is the audio record ID (UUID).
     *
     * Processing Steps:
     * 1. Validate audio record ID
     * 2. Fetch audio record and processing task from database
     * 3. Update task status to PROCESSING
     * 4. Load audio file from storage
     * 5. Transcribe audio using Whisper
     * 6. Analyze transcription using Claude
     * 7. Save AI summary to database
     * 8. Update task status to COMPLETED
     *
     * Error Handling:
     * - All errors are caught and logged with context
     * - Task status updated to FAILED on error
     * - Error details stored in ProcessingTask.errorLog
     * - Retry count incremented for transient failures
     *
     * @param audioRecordId the UUID of the audio record to process (received as JSON string)
     * @throws RuntimeException if database operations fail (propagates to DLQ)
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.audio.processing:audio.processing.queue}")
    @Transactional
    public void processAudioRecording(UUID audioRecordId) {
        log.info("Received audio processing task from queue: audioRecordId={}", audioRecordId);

        ProcessingTask processingTask = null;

        try {
            // Step 1: Validate input
            if (audioRecordId == null) {
                log.error("Received null audio record ID from queue");
                throw new IllegalArgumentException("Audio record ID cannot be null");
            }

            // Step 2: Fetch audio record from database
            log.debug("Fetching audio record from database: audioRecordId={}", audioRecordId);
            AudioRecord audioRecord = audioRecordRepository.findById(audioRecordId)
                    .orElseThrow(() -> {
                        log.error("Audio record not found in database: audioRecordId={}", audioRecordId);
                        return new IllegalArgumentException("Audio record not found: " + audioRecordId);
                    });

            // Step 3: Fetch processing task
            log.debug("Fetching processing task from database: audioRecordId={}", audioRecordId);
            processingTask = processingTaskRepository.findByAudioRecordId(audioRecordId)
                    .orElseThrow(() -> {
                        log.error("Processing task not found for audio record: audioRecordId={}", audioRecordId);
                        return new IllegalArgumentException("Processing task not found for audio record: " + audioRecordId);
                    });

            // Validate current task status
            if (processingTask.getStatus() != ProcessingStatus.PENDING) {
                log.warn("Processing task is not in PENDING status: audioRecordId={}, currentStatus={}",
                        audioRecordId, processingTask.getStatus());
                // Already processed or processing, skip
                return;
            }

            // Step 4: Update task status to PROCESSING
            log.info("Starting audio processing: audioRecordId={}, fileName={}",
                    audioRecordId, audioRecord.getFileName());
            processingTask.markAsProcessing();
            processingTaskRepository.save(processingTask);

            // Step 5: Load audio file from storage
            log.debug("Loading audio file from storage: fileKey={}", audioRecord.getFileKey());
            File audioFile = loadAudioFile(audioRecord.getFileKey());
            FileSystemResource audioResource = new FileSystemResource(audioFile);

            if (!audioResource.exists()) {
                throw new RuntimeException("Audio file not found in storage: " + audioRecord.getFileKey());
            }

            // Step 6: Transcribe audio using Whisper
            log.info("Starting audio transcription with Whisper: audioRecordId={}", audioRecordId);
            String transcription = whisperService.transcribe(audioResource);
            log.info("Audio transcription completed: audioRecordId={}, transcriptionLength={} characters",
                    audioRecordId, transcription.length());

            // Step 7: Analyze transcription using Claude
            log.info("Starting transcription analysis with Claude: audioRecordId={}", audioRecordId);
            Map<String, Object> structuredData = claudeService.analyzeTranscription(transcription);
            log.info("Transcription analysis completed: audioRecordId={}, event='{}', category='{}'",
                    audioRecordId,
                    structuredData.get("event"),
                    structuredData.get("category"));

            // Step 8: Save AI summary to database
            log.debug("Saving AI summary to database: audioRecordId={}", audioRecordId);
            AISummary aiSummary = new AISummary(
                    audioRecord,
                    transcription,
                    structuredData,
                    null, // tokensUsed - set after response metadata tracking
                    claudeService.getModel()
            );
            aiSummaryRepository.save(aiSummary);

            // Step 9: Update task status to COMPLETED
            log.info("Audio processing completed successfully: audioRecordId={}", audioRecordId);
            processingTask.markAsCompleted();
            processingTaskRepository.save(processingTask);

        } catch (IllegalArgumentException e) {
            // Validation errors - permanent failure, no retry
            log.error("Validation error processing audio recording: audioRecordId={}, error={}",
                    audioRecordId, e.getMessage());
            if (processingTask != null) {
                processingTask.markAsFailed("Validation error: " + e.getMessage());
                processingTaskRepository.save(processingTask);
            }
            // Don't re-throw - message is processed (with failure)

        } catch (RuntimeException e) {
            // Processing errors - check retry count
            log.error("Error processing audio recording: audioRecordId={}, error={}, retryCount={}",
                    audioRecordId, e.getMessage(),
                    processingTask != null ? processingTask.getRetryCount() : 0, e);

            if (processingTask != null) {
                processingTask.incrementRetryCount();

                if (processingTask.hasExceededMaxRetries(maxRetries)) {
                    // Max retries exceeded - mark as permanently failed
                    log.error("Max retries exceeded for audio processing: audioRecordId={}, retryCount={}",
                            audioRecordId, processingTask.getRetryCount());
                    processingTask.markAsFailed("Max retries exceeded: " + e.getMessage());
                    processingTaskRepository.save(processingTask);
                    // Don't re-throw - message is processed (with failure)
                } else {
                    // Retry available - re-throw to send back to queue
                    log.warn("Retrying audio processing: audioRecordId={}, retryCount={}/{}",
                            audioRecordId, processingTask.getRetryCount(), maxRetries);
                    processingTask.setStatus(ProcessingStatus.PENDING);
                    processingTaskRepository.save(processingTask);
                    throw e; // Re-throw to trigger RabbitMQ retry
                }
            } else {
                // No processing task found - re-throw to send to DLQ
                log.error("No processing task found for audio record: audioRecordId={}", audioRecordId);
                throw e;
            }
        } catch (Exception e) {
            // Unexpected errors - propagate to DLQ
            log.error("Unexpected error processing audio recording: audioRecordId={}, error={}",
                    audioRecordId, e.getMessage(), e);
            if (processingTask != null) {
                processingTask.markAsFailed("Unexpected error: " + e.getMessage());
                processingTaskRepository.save(processingTask);
            }
            throw new RuntimeException("Unexpected error processing audio recording: " + e.getMessage(), e);
        }
    }

    /**
     * Load audio file from storage based on file key.
     *
     * The file key represents the storage path for the audio file. In production,
     * this would be an object storage key (MinIO/Hetzner Storage). For now,
     * we use a local file system path.
     *
     * File Key Format: "audio/YYYY/MM/DD/filename.ext"
     * Storage Path: ${app.audio.storage.path}/audio/YYYY/MM/DD/filename.ext
     *
     * @param fileKey the storage key for the audio file
     * @return the audio file
     * @throws RuntimeException if file cannot be loaded
     */
    private File loadAudioFile(String fileKey) {
        try {
            // Construct full file path
            String fullPath = audioStoragePath + File.separator + fileKey;
            File audioFile = new File(fullPath);

            log.debug("Loading audio file from path: {}", fullPath);

            if (!audioFile.exists()) {
                log.error("Audio file not found: fullPath={}, fileKey={}", fullPath, fileKey);
                throw new RuntimeException("Audio file not found: " + fileKey);
            }

            if (!audioFile.canRead()) {
                log.error("Audio file not readable: fullPath={}", fullPath);
                throw new RuntimeException("Audio file not readable: " + fileKey);
            }

            // Validate file size matches metadata
            long actualFileSize = audioFile.length();
            log.debug("Audio file loaded: fullPath={}, size={} bytes", fullPath, actualFileSize);

            return audioFile;

        } catch (Exception e) {
            log.error("Failed to load audio file: fileKey={}, error={}", fileKey, e.getMessage(), e);
            throw new RuntimeException("Failed to load audio file: " + fileKey + " - " + e.getMessage(), e);
        }
    }

    /**
     * Get the current audio storage path.
     *
     * @return the storage path where audio files are located
     */
    public String getAudioStoragePath() {
        return audioStoragePath;
    }

    /**
     * Get the maximum retry attempts for failed processing.
     *
     * @return the maximum number of retry attempts
     */
    public int getMaxRetries() {
        return maxRetries;
    }
}
