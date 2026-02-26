package com.prod.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for audio record details.
 *
 * This DTO is returned to clients when retrieving audio record information.
 * It contains the complete metadata for an audio recording including its
 * processing status and timestamps.
 *
 * Use Cases:
 * 1. After successful audio upload - Returns created record details
 * 2. GET /api/audio/{id} - Returns single audio record with summary
 * 3. GET /api/audio - Returns list of audio records (paginated)
 *
 * Example JSON response:
 * <pre>
 * {
 *   "id": "550e8400-e29b-41d4-a716-446655440000",
 *   "fileName": "meeting-recording.mp3",
 *   "fileKey": "audio/2024/01/15/550e8400-e29b-41d4-a716-446655440000.mp3",
 *   "durationSeconds": 120,
 *   "mimeType": "audio/mpeg",
 *   "fileSizeBytes": 2400000,
 *   "createdAt": "2024-01-15T10:30:00",
 *   "updatedAt": "2024-01-15T10:30:00",
 *   "processingStatus": "COMPLETED",
 *   "summaryId": "a1234567-e89b-12d3-a456-426614174000"
 * }
 * </pre>
 *
 * Processing Status Values:
 * - PENDING: Queued for processing
 * - PROCESSING: Currently being transcribed and analyzed
 * - COMPLETED: Processing finished successfully
 * - FAILED: Processing failed (check error log)
 *
 * @see com.prod.dto.request.AudioUploadRequest
 * @see com.prod.dto.response.ProcessingStatusResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioRecordResponse {

    /**
     * Unique identifier of the audio record.
     *
     * UUID format for better security and scalability.
     * Used to retrieve the full record or its summary.
     */
    private UUID id;

    /**
     * Original filename as uploaded by the user.
     *
     * Display name shown in the dashboard and UI.
     */
    private String fileName;

    /**
     * Object storage key/path for the audio file.
     *
     * Used to retrieve the actual audio file from object storage.
     * This is the internal storage identifier, not typically shown to users.
     */
    private String fileKey;

    /**
     * Duration of the audio recording in seconds.
     *
     * Displayed in the dashboard as formatted time (e.g., "2:00").
     */
    private Integer durationSeconds;

    /**
     * MIME type of the audio file.
     *
     * Indicates the audio format (e.g., "audio/mpeg", "audio/wav").
     */
    private String mimeType;

    /**
     * File size in bytes.
     *
     * Displayed in the dashboard as formatted size (e.g., "2.4 MB").
     */
    private Long fileSizeBytes;

    /**
     * Timestamp when the audio record was created.
     *
     * Automatically set by the database when the record is first saved.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when the audio record was last updated.
     *
     * Automatically updated by the database when any field changes.
     */
    private LocalDateTime updatedAt;

    /**
     * Current processing status of the audio record.
     *
     * Indicates the state of the asynchronous AI processing:
     * - PENDING: Queued, waiting to be processed
     * - PROCESSING: Currently being transcribed/analyzed
     * - COMPLETED: Processing finished, summary available
     * - FAILED: Processing failed with errors
     */
    private String processingStatus;

    /**
     * ID of the AI summary generated from this audio record.
     *
     * Only populated when processingStatus is COMPLETED.
     * Can be used to retrieve the full summary details.
     */
    private UUID summaryId;

    /**
     * Error message if processing failed.
     *
     * Only populated when processingStatus is FAILED.
     * Contains details about what went wrong during processing.
     */
    private String errorMessage;
}
