package com.prod.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for AI-generated summaries.
 *
 * This DTO is returned to clients when retrieving AI analysis results from
 * audio recordings. It contains both the raw transcription text and the
 * structured data extracted by Claude 3.5 Sonnet.
 *
 * Use Cases:
 * 1. GET /api/summaries - List all summaries for authenticated user (paginated)
 * 2. GET /api/summaries/{id} - Get single summary with full details
 * 3. Nested in AudioRecordResponse when audio processing is complete
 *
 * Example JSON response:
 * <pre>
 * {
 *   "id": "a1234567-e89b-12d3-a456-426614174000",
 *   "audioRecordId": "550e8400-e29b-41d4-a716-446655440000",
 *   "content": "In today's meeting, we discussed the requirements for the new API...",
 *   "structuredData": {
 *     "event": "Requirements meeting with tech team",
 *     "duration_minutes": 40,
 *     "category": "meeting",
 *     "action_items": [
 *       "Review API specifications",
 *       "Schedule follow-up"
 *     ]
 *   },
 *   "tokensUsed": 1250,
 *   "model": "anthropic/claude-3.5-sonnet",
 *   "createdAt": "2024-01-15T10:31:00"
 * }
 * </pre>
 *
 * Structured Data Format:
 * The structuredData field contains JSON extracted by Claude with the following
 * possible keys:
 * - event: Description of the event/activity
 * - duration_minutes: Duration in minutes
 * - category: Type of activity (meeting, work, personal, etc.)
 * - action_items: Array of action item strings
 *
 * @see com.prod.entity.AISummary
 * @see com.prod.dto.response.AudioRecordResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryResponse {

    /**
     * Unique identifier of the AI summary.
     *
     * UUID format for better security and scalability.
     * Used to retrieve the full summary details or associated action items.
     */
    private UUID id;

    /**
     * ID of the audio record that was processed to generate this summary.
     *
     * Links the summary back to the original audio recording.
     * Can be used to retrieve the audio metadata.
     */
    private UUID audioRecordId;

    /**
     * Raw transcription text from Whisper STT model.
     *
     * Contains the complete text transcription of the audio recording.
     * This is the unprocessed text output from the speech-to-text model.
     */
    private String content;

    /**
     * Structured data extracted by Claude 3.5 Sonnet from the transcription.
     *
     * This JSON object contains the AI-extracted information including:
     * - event: Event description
     * - duration_minutes: Duration in minutes
     * - category: Activity category
     * - action_items: Array of action item descriptions
     *
     * The structure is flexible and may include additional fields based on
     * the AI analysis.
     */
    private Map<String, Object> structuredData;

    /**
     * Total number of tokens consumed in AI processing.
     *
     * Includes tokens from both Whisper transcription and Claude analysis.
     * Used for usage tracking and cost estimation.
     */
    private Integer tokensUsed;

    /**
     * AI model identifier used for analysis.
     *
     * Example: "anthropic/claude-3.5-sonnet"
     * Used for tracking which model version generated this summary.
     */
    private String model;

    /**
     * Timestamp when the AI summary was created.
     *
     * Automatically set when the summary is saved to the database.
     * Indicates when the audio processing completed.
     */
    private LocalDateTime createdAt;

    /**
     * Extracted event description from structured data.
     *
     * Convenience accessor for the "event" field in structuredData.
     * Returns null if the field is not present.
     */
    public String getEvent() {
        return structuredData != null ? (String) structuredData.get("event") : null;
    }

    /**
     * Extracted duration in minutes from structured data.
     *
     * Convenience accessor for the "duration_minutes" field in structuredData.
     * Returns null if the field is not present.
     */
    public Integer getDurationMinutes() {
        return structuredData != null ? (Integer) structuredData.get("duration_minutes") : null;
    }

    /**
     * Extracted category from structured data.
     *
     * Convenience accessor for the "category" field in structuredData.
     * Returns null if the field is not present.
     */
    public String getCategory() {
        return structuredData != null ? (String) structuredData.get("category") : null;
    }
}
