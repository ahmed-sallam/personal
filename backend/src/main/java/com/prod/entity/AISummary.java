package com.prod.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AISummary entity for storing AI-generated analysis results.
 *
 * Represents AI analysis results from audio transcription and NLU processing.
 * Stores both the raw transcription text and structured data extracted by Claude
 * 3.5 Sonnet in JSONB format for flexible querying and storage.
 *
 * Database Table: ai_summaries
 */
@Entity
@Table(name = "ai_summaries", indexes = {
    @Index(name = "idx_summary_audio_id", columnList = "audio_id"),
    @Index(name = "idx_summary_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AISummary {

    /**
     * Primary key using UUID for better security and scalability.
     * Generated automatically using database UUID generation.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Audio record that was processed to generate this summary.
     * Foreign key relationship to AudioRecord entity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audio_id", nullable = false, foreignKey = @ForeignKey(name = "fk_summary_audio"))
    private AudioRecord audioRecord;

    /**
     * Raw transcription text from Whisper STT model.
     * Contains the complete text transcription of the audio recording.
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Structured data extracted by Claude 3.5 Sonnet from the transcription.
     * Stored as JSONB in PostgreSQL for flexible querying and indexing.
     *
     * Example structure:
     * {
     *   "event": "Requirements meeting with tech team",
     *   "duration_minutes": 40,
     *   "category": "meeting",
     *   "action_items": ["Review API specifications", "Schedule follow-up"]
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_data", columnDefinition = "jsonb")
    private Map<String, Object> structuredData;

    /**
     * Total number of tokens consumed in AI processing.
     * Includes tokens from both Whisper transcription and Claude analysis.
     * Used for usage tracking and cost estimation.
     */
    @Column(name = "tokens_used")
    private Integer tokensUsed;

    /**
     * AI model identifier used for analysis.
     * Example: "anthropic/claude-3.5-sonnet"
     * Used for tracking which model version generated this summary.
     */
    @Column(name = "model", length = 100)
    private String model;

    /**
     * Timestamp of when the AI summary was created.
     * Automatically set by Hibernate on entity creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Constructor for creating a new AI summary with essential data.
     *
     * @param audioRecord the audio record that was processed
     * @param content the raw transcription text
     * @param structuredData the structured data extracted by AI
     * @param tokensUsed the total tokens consumed
     * @param model the AI model identifier
     */
    public AISummary(AudioRecord audioRecord, String content, Map<String, Object> structuredData,
                     Integer tokensUsed, String model) {
        this.audioRecord = audioRecord;
        this.content = content;
        this.structuredData = structuredData;
        this.tokensUsed = tokensUsed;
        this.model = model;
    }
}
