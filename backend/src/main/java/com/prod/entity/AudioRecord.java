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
 * AudioRecord entity for storing audio file metadata.
 *
 * Represents audio recordings uploaded by users in the productivity tracking system.
 * Stores file metadata including storage key, duration, and mime type. The actual
 * audio file content is stored in object storage (MinIO/Hetzner Storage).
 *
 * Database Table: audio_records
 */
@Entity
@Table(name = "audio_records", indexes = {
    @Index(name = "idx_audio_user_id", columnList = "user_id"),
    @Index(name = "idx_audio_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioRecord {

    /**
     * Primary key using UUID for better security and scalability.
     * Generated automatically using database UUID generation.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * User who uploaded this audio record.
     * Foreign key relationship to User entity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_audio_user"))
    private User user;

    /**
     * Object storage key/path for the audio file.
     * Used to retrieve the actual audio file from object storage.
     * Format example: "audio/2024/01/15/abc-def-ghi.mp3"
     */
    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    /**
     * Original filename as uploaded by the user.
     * Preserved for user reference and display purposes.
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * Duration of the audio recording in seconds.
     * Used for validation and display purposes.
     */
    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    /**
     * MIME type of the audio file (e.g., "audio/mpeg", "audio/wav").
     * Used for validation and proper content-type handling.
     */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /**
     * File size in bytes.
     * Used for storage management and display purposes.
     */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /**
     * Timestamp of when the audio record was created.
     * Automatically set by Hibernate on entity creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of when the audio record was last updated.
     * Automatically updated by Hibernate on entity modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Constructor for creating a new audio record with essential metadata.
     *
     * @param user the user who uploaded the audio
     * @param fileKey the object storage key
     * @param fileName the original filename
     * @param durationSeconds the audio duration in seconds
     * @param mimeType the MIME type of the audio file
     * @param fileSizeBytes the file size in bytes
     */
    public AudioRecord(User user, String fileKey, String fileName, Integer durationSeconds,
                       String mimeType, Long fileSizeBytes) {
        this.user = user;
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.durationSeconds = durationSeconds;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
    }
}
