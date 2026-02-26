package com.prod.repository;

import com.prod.entity.AudioRecord;
import com.prod.entity.ProcessingTask;
import com.prod.entity.ProcessingTask.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ProcessingTask entity operations.
 *
 * Provides database access methods for audio processing task management,
 * including status tracking, retry logic, and monitoring operations.
 *
 * Spring Data JPA will automatically implement this interface at runtime.
 */
@Repository
public interface ProcessingTaskRepository extends JpaRepository<ProcessingTask, UUID> {

    /**
     * Find a processing task by its associated audio record.
     * Useful for checking the processing status of a specific audio.
     *
     * @param audioRecord the audio record to search for
     * @return Optional containing the processing task if found, empty otherwise
     */
    Optional<ProcessingTask> findByAudioRecord(AudioRecord audioRecord);

    /**
     * Find a processing task by audio record ID.
     * Alternative lookup method using audio ID instead of entity.
     *
     * @param audioId the audio record ID
     * @return Optional containing the processing task if found, empty otherwise
     */
    @Query("SELECT pt FROM ProcessingTask pt WHERE pt.audioRecord.id = :audioId")
    Optional<ProcessingTask> findByAudioRecordId(@Param("audioId") UUID audioId);

    /**
     * Find all processing tasks with a specific status.
     * Useful for monitoring and queue management (e.g., finding stuck PROCESSING tasks).
     *
     * @param status the processing status to filter by
     * @return List of processing tasks with the specified status
     */
    List<ProcessingTask> findByStatus(ProcessingStatus status);

    /**
     * Find all processing tasks with a specific status, paginated.
     * Useful for dashboard displays and batch operations.
     *
     * @param status the processing status to filter by
     * @param pageable pagination parameters
     * @return Page of processing tasks with the specified status
     */
    Page<ProcessingTask> findByStatus(ProcessingStatus status, Pageable pageable);

    /**
     * Find processing tasks for a specific user by status.
     * Joins through audio_records to filter by user ownership.
     *
     * @param userId the user ID
     * @param status the processing status to filter by
     * @return List of processing tasks matching criteria
     */
    @Query("SELECT pt FROM ProcessingTask pt WHERE pt.audioRecord.user.id = :userId AND pt.status = :status")
    List<ProcessingTask> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") ProcessingStatus status);

    /**
     * Find stuck processing tasks that have been in PROCESSING state for too long.
     * Used for monitoring and automatic recovery of hung tasks.
     *
     * @param threshold the time threshold (tasks processing longer than this are considered stuck)
     * @return List of stuck processing tasks
     */
    @Query("SELECT pt FROM ProcessingTask pt WHERE pt.status = 'PROCESSING' AND pt.startedAt < :threshold")
    List<ProcessingTask> findStuckProcessingTasks(@Param("threshold") LocalDateTime threshold);

    /**
     * Find failed tasks that are eligible for retry.
     * Returns tasks with FAILED status that haven't exceeded max retry count.
     *
     * @param maxRetries maximum retry count threshold
     * @return List of failed tasks eligible for retry
     */
    @Query("SELECT pt FROM ProcessingTask pt WHERE pt.status = 'FAILED' AND pt.retryCount < :maxRetries")
    List<ProcessingTask> findFailedTasksEligibleForRetry(@Param("maxRetries") int maxRetries);

    /**
     * Count processing tasks by status.
     * Useful for dashboard statistics and monitoring.
     *
     * @param status the processing status to count
     * @return number of tasks with the specified status
     */
    long countByStatus(ProcessingStatus status);

    /**
     * Count processing tasks by status for a specific user.
     *
     * @param userId the user ID
     * @param status the processing status to count
     * @return number of tasks matching criteria
     */
    @Query("SELECT COUNT(pt) FROM ProcessingTask pt WHERE pt.audioRecord.user.id = :userId AND pt.status = :status")
    long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") ProcessingStatus status);

    /**
     * Find the most recent processing task for each audio record of a user.
     * Useful for displaying user's audio processing history.
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return Page of processing tasks
     */
    @Query("SELECT pt FROM ProcessingTask pt WHERE pt.audioRecord.user.id = :userId ORDER BY pt.createdAt DESC")
    Page<ProcessingTask> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Check if a processing task exists for a specific audio record.
     *
     * @param audioRecord the audio record to check
     * @return true if a processing task exists, false otherwise
     */
    boolean existsByAudioRecord(AudioRecord audioRecord);

    /**
     * Delete all processing tasks associated with an audio record.
     * Used for cleanup operations.
     *
     * @param audioRecord the audio record whose tasks should be deleted
     */
    void deleteByAudioRecord(AudioRecord audioRecord);
}
