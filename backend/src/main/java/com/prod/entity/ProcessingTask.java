package com.prod.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ProcessingTask entity for tracking asynchronous audio processing status.
 *
 * Represents the processing state of audio records as they move through the
 * RabbitMQ queue and AI processing pipeline (Whisper transcription + Claude analysis).
 * Status transitions: PENDING → PROCESSING → COMPLETED/FAILED
 *
 * Database Table: processing_tasks
 */
@Entity
@Table(name = "processing_tasks", indexes = {
    @Index(name = "idx_processing_audio_id", columnList = "audio_id"),
    @Index(name = "idx_processing_status", columnList = "status"),
    @Index(name = "idx_processing_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingTask {

    /**
     * Primary key using UUID for better security and scalability.
     * Generated automatically using database UUID generation.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Audio record being processed.
     * Foreign key relationship to AudioRecord entity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audio_id", nullable = false, foreignKey = @ForeignKey(name = "fk_processing_audio"))
    private AudioRecord audioRecord;

    /**
     * Current processing status of the task.
     * Tracks the lifecycle: PENDING → PROCESSING → COMPLETED/FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProcessingStatus status = ProcessingStatus.PENDING;

    /**
     * Error log for failed processing attempts.
     * Stores error messages and stack traces for debugging when status is FAILED.
     */
    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    /**
     * Number of retry attempts for failed processing.
     * Used to implement retry logic (max 3 retries before permanent failure).
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * Timestamp when processing started (status changed to PROCESSING).
     * Used for monitoring processing duration and detecting stuck tasks.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * Timestamp when processing completed (status changed to COMPLETED/FAILED).
     * Used for calculating total processing time and analytics.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Timestamp of when the processing task was created.
     * Automatically set by Hibernate on entity creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of when the processing task was last updated.
     * Automatically updated by Hibernate on entity modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Constructor for creating a new processing task for an audio record.
     *
     * @param audioRecord the audio record to process
     */
    public ProcessingTask(AudioRecord audioRecord) {
        this.audioRecord = audioRecord;
        this.status = ProcessingStatus.PENDING;
        this.retryCount = 0;
    }

    /**
     * Processing status enumeration.
     * Represents the lifecycle states of an audio processing task.
     */
    public enum ProcessingStatus {
        /**
         * Task is queued and waiting for processing.
         * Initial state when audio is uploaded.
         */
        PENDING,

        /**
         * Task is currently being processed by a consumer.
         * AI services (Whisper + Claude) are working on the audio.
         */
        PROCESSING,

        /**
         * Processing completed successfully.
         * Transcription and analysis are stored in ai_summaries table.
         */
        COMPLETED,

        /**
         * Processing failed after all retry attempts.
         * Error details are stored in error_log field.
         */
        FAILED
    }

    /**
     * Mark the task as processing and record the start time.
     */
    public void markAsProcessing() {
        this.status = ProcessingStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Mark the task as completed and record the completion time.
     */
    public void markAsCompleted() {
        this.status = ProcessingStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Mark the task as failed with an error message and record the completion time.
     *
     * @param errorMessage the error message to log
     */
    public void markAsFailed(String errorMessage) {
        this.status = ProcessingStatus.FAILED;
        this.errorLog = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Increment the retry count for failed processing attempts.
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Check if the task has exceeded the maximum retry attempts.
     *
     * @param maxRetries maximum number of retry attempts allowed
     * @return true if retry count exceeds max retries, false otherwise
     */
    public boolean hasExceededMaxRetries(int maxRetries) {
        return this.retryCount >= maxRetries;
    }
}
