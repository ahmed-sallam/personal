package com.prod.repository;

import com.prod.entity.AISummary;
import com.prod.entity.AudioRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for AISummary entity.
 *
 * Provides CRUD operations and custom queries for AI-generated summaries.
 * Extends Spring Data JPA's JpaRepository for automatic implementation
 * of common database operations.
 */
@Repository
public interface AISummaryRepository extends JpaRepository<AISummary, UUID> {

    /**
     * Find AI summary by associated audio record.
     *
     * @param audioRecord the audio record to search for
     * @return Optional containing the AISummary if found, empty otherwise
     */
    Optional<AISummary> findByAudioRecord(AudioRecord audioRecord);

    /**
     * Find AI summary by audio record ID.
     *
     * @param audioId the audio record UUID
     * @return Optional containing the AISummary if found, empty otherwise
     */
    Optional<AISummary> findByAudioRecordId(UUID audioId);

    /**
     * Check if an AI summary exists for a given audio record.
     *
     * @param audioRecord the audio record to check
     * @return true if summary exists, false otherwise
     */
    boolean existsByAudioRecord(AudioRecord audioRecord);
}
