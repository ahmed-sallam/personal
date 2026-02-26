package com.prod.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for audio upload.
 *
 * This DTO is used when a user uploads an audio file for processing.
 * The system will:
 * 1. Validate the audio file metadata
 * 2. Create an AudioRecord entity with the provided metadata
 * 3. Create a ProcessingTask with PENDING status
 * 4. Send the audio record ID to RabbitMQ for asynchronous processing
 * 5. Return immediately with audio record ID and processing task ID
 *
 * Validation:
 * - fileKey must not be blank (object storage path)
 * - fileName must not be blank (original filename)
 * - durationSeconds must be positive (no zero-duration audio)
 * - mimeType must not be blank and must start with "audio/"
 * - fileSizeBytes must be positive (no empty files)
 *
 * Example JSON request:
 * <pre>
 * {
 *   "fileKey": "audio/2024/01/15/recording-abc123.mp3",
 *   "fileName": "meeting-recording.mp3",
 *   "durationSeconds": 120,
 *   "mimeType": "audio/mpeg",
 *   "fileSizeBytes": 2400000
 * }
 * </pre>
 *
 * Note: The actual audio file content is stored in object storage (MinIO/Hetzner).
 * This DTO contains only the metadata and storage key for the uploaded file.
 *
 * @see com.prod.dto.response.AudioRecordResponse
 * @see com.prod.dto.response.ProcessingStatusResponse
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioUploadRequest {

    /**
     * Object storage key/path for the audio file.
     *
     * This is the identifier used to retrieve the actual audio file from
     * object storage (e.g., MinIO or Hetzner Storage). The file itself
     * should be uploaded to object storage before this API is called.
     *
     * Format examples:
     * - "audio/2024/01/15/uuid-filename.mp3"
     * - "user-123/recordings/recording-abc.wav"
     *
     * Must not be blank and should be a valid storage key path.
     */
    @NotBlank(message = "File key is required")
    private String fileKey;

    /**
     * Original filename as uploaded by the user.
     *
     * Preserved for user reference and display purposes in the dashboard.
     * This is the filename the user selected when uploading the file.
     *
     * Example: "meeting-recording.mp3", "voice-note-2024-01-15.wav"
     */
    @NotBlank(message = "File name is required")
    private String fileName;

    /**
     * Duration of the audio recording in seconds.
     *
     * Used for validation (must be > 0) and display purposes.
     * This value should be extracted from the audio file metadata
     * before calling this API.
     *
     * Must be a positive integer (zero or negative values are rejected).
     */
    @NotNull(message = "Duration seconds is required")
    @Positive(message = "Duration seconds must be greater than 0")
    private Integer durationSeconds;

    /**
     * MIME type of the audio file.
     *
     * Indicates the format of the audio file for proper processing.
     * Common values include:
     * - "audio/mpeg" (MP3)
     * - "audio/wav" (WAV)
     * - "audio/m4a" (M4A)
     * - "audio/webm" (WebM)
     *
     * Must start with "audio/" prefix to be accepted.
     */
    @NotBlank(message = "MIME type is required")
    private String mimeType;

    /**
     * File size in bytes.
     *
     * Used for validation (must be > 0) and storage management.
     * This value should be obtained from the uploaded file metadata.
     *
     * Must be a positive integer (zero or negative values are rejected).
     */
    @NotNull(message = "File size bytes is required")
    @Positive(message = "File size bytes must be greater than 0")
    private Long fileSizeBytes;
}
