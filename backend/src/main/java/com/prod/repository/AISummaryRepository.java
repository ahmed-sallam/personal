package com.prod.repository;

import com.prod.entity.AISummary;
import com.prod.entity.AudioRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Find all AI summaries for a specific user.
     * Joins through audio record to get user's summaries.
     *
     * @param userId the user UUID
     * @param pageable pagination and sorting parameters
     * @return Page of AI summaries for the user
     */
    @Query("SELECT s FROM AISummary s " +
           "JOIN s.audioRecord ar " +
           "WHERE ar.user.id = :userId " +
           "ORDER BY s.createdAt DESC")
    Page<AISummary> findByUserId(@Param("userId") UUID userId, Pageable pageable);
}
