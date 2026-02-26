package com.prod.repository;

import com.prod.entity.AudioRecord;
import com.prod.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for AudioRecord entity operations.
 *
 * Provides database access methods for audio record management, including
 * user-specific queries, pagination, and filtering by date ranges.
 *
 * Spring Data JPA will automatically implement this interface at runtime.
 */
@Repository
public interface AudioRecordRepository extends JpaRepository<AudioRecord, UUID> {

    /**
     * Find all audio records for a specific user, ordered by creation date descending.
     *
     * @param user the user whose audio records to retrieve
     * @param pageable pagination information
     * @return page of audio records for the user
     */
    Page<AudioRecord> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Find all audio records for a specific user (non-paginated).
     *
     * @param user the user whose audio records to retrieve
     * @return list of audio records for the user
     */
    List<AudioRecord> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Find audio record by ID and user (for authorization checks).
     * Ensures users can only access their own audio records.
     *
     * @param id the audio record ID
     * @param user the user who should own this record
     * @return Optional containing the audio record if found and owned by user, empty otherwise
     */
    Optional<AudioRecord> findByIdAndUser(UUID id, User user);

    /**
     * Find audio records by user within a date range.
     * Useful for filtering dashboard data by time period.
     *
     * @param user the user whose audio records to retrieve
     * @param startDate the start of the date range (inclusive)
     * @param endDate the end of the date range (inclusive)
     * @param pageable pagination information
     * @return page of audio records within the date range
     */
    Page<AudioRecord> findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
            User user, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Count total audio records for a user.
     * Useful for statistics and dashboard metrics.
     *
     * @param user the user whose audio records to count
     * @return total count of audio records
     */
    long countByUser(User user);

    /**
     * Check if an audio record exists for a given user and ID.
     * Used for quick existence checks without loading the full entity.
     *
     * @param id the audio record ID
     * @param user the user who should own this record
     * @return true if the record exists and is owned by the user, false otherwise
     */
    boolean existsByIdAndUser(UUID id, User user);

    /**
     * Find audio records by file key.
     * Useful for deduplication checks or object storage cleanup.
     *
     * @param fileKey the object storage key
     * @return Optional containing the audio record if found, empty otherwise
     */
    Optional<AudioRecord> findByFileKey(String fileKey);
}
