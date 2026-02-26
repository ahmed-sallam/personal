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
 * ActionItem entity for storing extracted action items from AI summaries.
 *
 * Represents individual tasks or action items extracted by Claude 3.5 Sonnet
 * from audio transcriptions. Each action item is associated with an AI summary
 * and can be tracked for completion status.
 *
 * Database Table: action_items
 */
@Entity
@Table(name = "action_items", indexes = {
    @Index(name = "idx_action_summary_id", columnList = "summary_id"),
    @Index(name = "idx_action_completed", columnList = "is_completed"),
    @Index(name = "idx_action_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionItem {

    /**
     * Primary key using UUID for better security and scalability.
     * Generated automatically using database UUID generation.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * AI summary from which this action item was extracted.
     * Foreign key relationship to AISummary entity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "summary_id", nullable = false, foreignKey = @ForeignKey(name = "fk_action_summary"))
    private AISummary aiSummary;

    /**
     * Description of the action item.
     * Contains the task text extracted from the AI analysis.
     * Example: "Review API specifications", "Schedule follow-up meeting"
     */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * Completion status of the action item.
     * True if the action has been completed, false otherwise.
     * Used for tracking task completion and productivity metrics.
     */
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    /**
     * Optional due date for the action item.
     * Can be set by the user or extracted from the transcription if mentioned.
     */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /**
     * Timestamp of when the action item was created.
     * Automatically set by Hibernate on entity creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of when the action item was last updated.
     * Automatically updated by Hibernate on entity modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Timestamp of when the action item was marked as completed.
     * Set when isCompleted is changed to true.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Constructor for creating a new action item with essential data.
     *
     * @param aiSummary the AI summary this action item belongs to
     * @param description the action item description
     */
    public ActionItem(AISummary aiSummary, String description) {
        this.aiSummary = aiSummary;
        this.description = description;
        this.isCompleted = false;
    }

    /**
     * Constructor for creating a new action item with due date.
     *
     * @param aiSummary the AI summary this action item belongs to
     * @param description the action item description
     * @param dueDate the optional due date
     */
    public ActionItem(AISummary aiSummary, String description, LocalDateTime dueDate) {
        this.aiSummary = aiSummary;
        this.description = description;
        this.isCompleted = false;
        this.dueDate = dueDate;
    }
}
