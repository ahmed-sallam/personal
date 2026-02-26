package com.prod.repository;

import com.prod.entity.ActionItem;
import com.prod.entity.AISummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ActionItem entity.
 *
 * Provides CRUD operations and custom queries for action items extracted
 * from AI summaries. Extends Spring Data JPA's JpaRepository for automatic
 * implementation of common database operations.
 */
@Repository
public interface ActionItemRepository extends JpaRepository<ActionItem, UUID> {

    /**
     * Find all action items for a given AI summary.
     *
     * @param aiSummary the AI summary to search for
     * @return List of action items associated with the summary
     */
    List<ActionItem> findByAiSummary(AISummary aiSummary);

    /**
     * Find all action items for a given AI summary ID.
     *
     * @param summaryId the AI summary UUID
     * @return List of action items associated with the summary
     */
    List<ActionItem> findByAiSummaryId(UUID summaryId);

    /**
     * Find all action items for a specific user.
     * Joins through AI summary and audio record to get user's action items.
     *
     * @param userId the user UUID
     * @param pageable pagination and sorting parameters
     * @return Page of action items for the user
     */
    @Query("SELECT ai FROM ActionItem ai " +
           "JOIN ai.aiSummary s " +
           "JOIN s.audioRecord ar " +
           "WHERE ar.user.id = :userId " +
           "ORDER BY ai.createdAt DESC")
    Page<ActionItem> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find all incomplete action items for a specific user.
     *
     * @param userId the user UUID
     * @param pageable pagination and sorting parameters
     * @return Page of incomplete action items for the user
     */
    @Query("SELECT ai FROM ActionItem ai " +
           "JOIN ai.aiSummary s " +
           "JOIN s.audioRecord ar " +
           "WHERE ar.user.id = :userId AND ai.isCompleted = false " +
           "ORDER BY ai.createdAt DESC")
    Page<ActionItem> findIncompleteByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find all completed action items for a specific user.
     *
     * @param userId the user UUID
     * @param pageable pagination and sorting parameters
     * @return Page of completed action items for the user
     */
    @Query("SELECT ai FROM ActionItem ai " +
           "JOIN ai.aiSummary s " +
           "JOIN s.audioRecord ar " +
           "WHERE ar.user.id = :userId AND ai.isCompleted = true " +
           "ORDER BY ai.completedAt DESC")
    Page<ActionItem> findCompletedByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find all action items by completion status.
     *
     * @param isCompleted completion status to filter by
     * @param pageable pagination and sorting parameters
     * @return Page of action items matching the completion status
     */
    Page<ActionItem> findByIsCompleted(Boolean isCompleted, Pageable pageable);

    /**
     * Find all overdue action items for a specific user.
     * Action items are considered overdue if they have a due date in the past
     * and are not yet completed.
     *
     * @param userId the user UUID
     * @param now the current date/time
     * @param pageable pagination and sorting parameters
     * @return Page of overdue action items
     */
    @Query("SELECT ai FROM ActionItem ai " +
           "JOIN ai.aiSummary s " +
           "JOIN s.audioRecord ar " +
           "WHERE ar.user.id = :userId " +
           "AND ai.isCompleted = false " +
           "AND ai.dueDate IS NOT NULL " +
           "AND ai.dueDate < :now " +
           "ORDER BY ai.dueDate ASC")
    Page<ActionItem> findOverdueByUserId(@Param("userId") UUID userId,
                                          @Param("now") LocalDateTime now,
                                          Pageable pageable);

    /**
     * Count total action items for a user.
     *
     * @param userId the user UUID
     * @return total count of action items
     */
    @Query("SELECT COUNT(ai) FROM ActionItem ai " +
           "JOIN ai.aiSummary s " +
           "JOIN s.audioRecord ar " +
           "WHERE ar.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);

    /**
     * Count incomplete action items for a user.
     *
     * @param userId the user UUID
     * @return count of incomplete action items
     */
    @Query("SELECT COUNT(ai) FROM ActionItem ai " +
           "JOIN ai.aiSummary s " +
           "JOIN s.audioRecord ar " +
           "WHERE ar.user.id = :userId AND ai.isCompleted = false")
    long countIncompleteByUserId(@Param("userId") UUID userId);

    /**
     * Count completed action items for a user.
     *
     * @param userId the user UUID
     * @return count of completed action items
     */
    @Query("SELECT COUNT(ai) FROM ActionItem ai " +
           "JOIN ai.aiSummary s " +
           "JOIN s.audioRecord ar " +
           "WHERE ar.user.id = :userId AND ai.isCompleted = true")
    long countCompletedByUserId(@Param("userId") UUID userId);
}
