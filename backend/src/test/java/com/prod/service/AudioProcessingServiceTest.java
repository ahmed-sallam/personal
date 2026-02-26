package com.prod.service;

import com.prod.entity.AudioRecord;
import com.prod.entity.ProcessingTask;
import com.prod.entity.User;
import com.prod.messaging.AudioProcessingProducer;
import com.prod.repository.AudioRecordRepository;
import com.prod.repository.ProcessingTaskRepository;
import com.prod.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AudioProcessingService.
 *
 * Tests the audio upload orchestration logic including:
 * - User authentication validation
 * - Request parameter validation
 * - AudioRecord and ProcessingTask creation
 * - RabbitMQ message publishing
 * - Transaction management
 *
 * @see com.prod.service.AudioProcessingService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AudioProcessingService Unit Tests")
class AudioProcessingServiceTest {

    @Mock
    private AudioRecordRepository audioRecordRepository;

    @Mock
    private ProcessingTaskRepository processingTaskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AudioProcessingProducer audioProcessingProducer;

    @InjectMocks
    private AudioProcessingService audioProcessingService;

    private User testUser;
    private Authentication authentication;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");
        testUser.setIsWhitelisted(true);

        // Create mock authentication
        authentication = new Authentication() {
            @Override
            public Collection<SimpleGrantedAuthority> getAuthorities() {
                return List.of(new SimpleGrantedAuthority("ROLE_USER"));
            }

            @Override
            public Object getCredentials() {
                return "credentials";
            }

            @Override
            public Object getDetails() {
                return "details";
            }

            @Override
            public Object getPrincipal() {
                return userId.toString();
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }

            @Override
            public void setAuthenticated(boolean isAuthenticated) {
                // Not implemented
            }

            @Override
            public String getName() {
                return userId.toString();
            }
        };
    }

    @Test
    @DisplayName("Successful audio upload should save records and send to queue")
    void testSuccessfulAudioUpload() {
        // Arrange
        String fileKey = "audio/test/meeting.mp3";
        String fileName = "meeting.mp3";
        Integer durationSeconds = 120;
        String mimeType = "audio/mpeg";
        Long fileSizeBytes = 240000L;

        UUID audioRecordId = UUID.randomUUID();
        UUID processingTaskId = UUID.randomUUID();

        AudioRecord audioRecord = new AudioRecord(
                testUser, fileKey, fileName, durationSeconds, mimeType, fileSizeBytes);
        audioRecord.setId(audioRecordId);

        ProcessingTask processingTask = new ProcessingTask(audioRecord);
        processingTask.setId(processingTaskId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(audioRecordRepository.save(any(AudioRecord.class))).thenReturn(audioRecord);
        when(processingTaskRepository.save(any(ProcessingTask.class))).thenReturn(processingTask);
        doNothing().when(audioProcessingProducer).sendProcessingTask(any(UUID.class));

        // Act
        AudioProcessingService.AudioUploadResponse response = audioProcessingService.processUpload(
                authentication,
                fileKey,
                fileName,
                durationSeconds,
                mimeType,
                fileSizeBytes
        );

        // Assert
        assertNotNull(response);
        assertEquals(audioRecordId, response.getAudioRecordId());
        assertEquals(processingTaskId, response.getProcessingTaskId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("Audio uploaded successfully. Processing started.", response.getMessage());

        // Verify database saves
        verify(audioRecordRepository, times(1)).save(any(AudioRecord.class));
        verify(processingTaskRepository, times(1)).save(any(ProcessingTask.class));

        // Verify queue message sent
        ArgumentCaptor<UUID> audioIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(audioProcessingProducer, times(1)).sendProcessingTask(audioIdCaptor.capture());
        assertEquals(audioRecordId, audioIdCaptor.getValue());
    }

    @Test
    @DisplayName("Upload with null authentication should throw exception")
    void testUploadWithNullAuthentication() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> audioProcessingService.processUpload(
                        null,
                        "audio/test/meeting.mp3",
                        "meeting.mp3",
                        120,
                        "audio/mpeg",
                        240000L
                )
        );

        assertEquals("User must be authenticated to upload audio", exception.getMessage());

        // Verify no database operations
        verify(audioRecordRepository, never()).save(any(AudioRecord.class));
        verify(processingTaskRepository, never()).save(any(ProcessingTask.class));
        verify(audioProcessingProducer, never()).sendProcessingTask(any(UUID.class));
    }

    @Test
    @DisplayName("Upload with empty file key should throw exception")
    void testUploadWithEmptyFileKey() {
        // Arrange - user lookup happens before validation
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> audioProcessingService.processUpload(
                        authentication,
                        "", // Empty file key
                        "meeting.mp3",
                        120,
                        "audio/mpeg",
                        240000L
                )
        );

        assertEquals("File key cannot be null or empty", exception.getMessage());

        // Verify no database operations
        verify(audioRecordRepository, never()).save(any(AudioRecord.class));
    }

    @Test
    @DisplayName("Upload with null file name should throw exception")
    void testUploadWithNullFileName() {
        // Arrange - user lookup happens before validation
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> audioProcessingService.processUpload(
                        authentication,
                        "audio/test/meeting.mp3",
                        null, // Null file name
                        120,
                        "audio/mpeg",
                        240000L
                )
        );

        assertEquals("File name cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Upload with zero duration should throw exception")
    void testUploadWithZeroDuration() {
        // Arrange - user lookup happens before validation
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> audioProcessingService.processUpload(
                        authentication,
                        "audio/test/meeting.mp3",
                        "meeting.mp3",
                        0, // Zero duration
                        "audio/mpeg",
                        240000L
                )
        );

        assertEquals("Duration seconds must be greater than 0", exception.getMessage());
    }

    @Test
    @DisplayName("Upload with negative duration should throw exception")
    void testUploadWithNegativeDuration() {
        // Arrange - user lookup happens before validation
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> audioProcessingService.processUpload(
                        authentication,
                        "audio/test/meeting.mp3",
                        "meeting.mp3",
                        -10, // Negative duration
                        "audio/mpeg",
                        240000L
                )
        );

        assertEquals("Duration seconds must be greater than 0", exception.getMessage());
    }

    @Test
    @DisplayName("Upload with invalid MIME type should throw exception")
    void testUploadWithInvalidMimeType() {
        // Arrange - user lookup happens before validation
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> audioProcessingService.processUpload(
                        authentication,
                        "audio/test/meeting.mp3",
                        "meeting.mp3",
                        120,
                        "video/mp4", // Invalid MIME type
                        240000L
                )
        );

        assertEquals("MIME type must be a valid audio type (e.g., audio/mpeg, audio/wav)", exception.getMessage());
    }

    @Test
    @DisplayName("Upload with zero file size should throw exception")
    void testUploadWithZeroFileSize() {
        // Arrange - user lookup happens before validation
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> audioProcessingService.processUpload(
                        authentication,
                        "audio/test/meeting.mp3",
                        "meeting.mp3",
                        120,
                        "audio/mpeg",
                        0L // Zero file size
                )
        );

        assertEquals("File size bytes must be greater than 0", exception.getMessage());
    }

    @Test
    @DisplayName("Upload with user not found in database should throw exception")
    void testUploadWithUserNotFound() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> audioProcessingService.processUpload(
                        authentication,
                        "audio/test/meeting.mp3",
                        "meeting.mp3",
                        120,
                        "audio/mpeg",
                        240000L
                )
        );

        assertEquals("Authenticated user not found", exception.getMessage());

        // Verify database query was made
        verify(userRepository, times(1)).findById(userId);

        // Verify no further database operations
        verify(audioRecordRepository, never()).save(any(AudioRecord.class));
    }

    @Test
    @DisplayName("Upload should save audio record with correct metadata")
    void testAudioRecordMetadataSaved() {
        // Arrange
        String fileKey = "audio/test/team-standup.mp3";
        String fileName = "team-standup.mp3";
        Integer durationSeconds = 300;
        String mimeType = "audio/mpeg";
        Long fileSizeBytes = 6000000L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(audioRecordRepository.save(any(AudioRecord.class)))
                .thenAnswer(invocation -> {
                    AudioRecord record = invocation.getArgument(0);
                    record.setId(UUID.randomUUID());
                    return record;
                });
        when(processingTaskRepository.save(any(ProcessingTask.class)))
                .thenAnswer(invocation -> {
                    ProcessingTask task = invocation.getArgument(0);
                    task.setId(UUID.randomUUID());
                    return task;
                });
        doNothing().when(audioProcessingProducer).sendProcessingTask(any(UUID.class));

        // Act
        audioProcessingService.processUpload(
                authentication,
                fileKey,
                fileName,
                durationSeconds,
                mimeType,
                fileSizeBytes
        );

        // Assert
        ArgumentCaptor<AudioRecord> audioRecordCaptor = ArgumentCaptor.forClass(AudioRecord.class);
        verify(audioRecordRepository).save(audioRecordCaptor.capture());

        AudioRecord savedRecord = audioRecordCaptor.getValue();
        assertEquals(testUser, savedRecord.getUser());
        assertEquals(fileKey, savedRecord.getFileKey());
        assertEquals(fileName, savedRecord.getFileName());
        assertEquals(durationSeconds, savedRecord.getDurationSeconds());
        assertEquals(mimeType, savedRecord.getMimeType());
        assertEquals(fileSizeBytes, savedRecord.getFileSizeBytes());
    }

    @Test
    @DisplayName("Upload should create processing task with PENDING status")
    void testProcessingTaskCreatedWithPendingStatus() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(audioRecordRepository.save(any(AudioRecord.class)))
                .thenAnswer(invocation -> {
                    AudioRecord record = invocation.getArgument(0);
                    record.setId(UUID.randomUUID());
                    return record;
                });
        when(processingTaskRepository.save(any(ProcessingTask.class)))
                .thenAnswer(invocation -> {
                    ProcessingTask task = invocation.getArgument(0);
                    task.setId(UUID.randomUUID());
                    return task;
                });
        doNothing().when(audioProcessingProducer).sendProcessingTask(any(UUID.class));

        // Act
        audioProcessingService.processUpload(
                authentication,
                "audio/test/meeting.mp3",
                "meeting.mp3",
                120,
                "audio/mpeg",
                240000L
        );

        // Assert
        ArgumentCaptor<ProcessingTask> taskCaptor = ArgumentCaptor.forClass(ProcessingTask.class);
        verify(processingTaskRepository).save(taskCaptor.capture());

        ProcessingTask savedTask = taskCaptor.getValue();
        assertEquals(ProcessingTask.ProcessingStatus.PENDING, savedTask.getStatus());
        assertEquals(0, savedTask.getRetryCount());
        assertNotNull(savedTask.getAudioRecord());
    }

    @Test
    @DisplayName("Valid audio MIME types should be accepted")
    void testValidAudioMimeTypes() {
        // Arrange
        String[] validMimeTypes = {
                "audio/mpeg",
                "audio/mp3",
                "audio/wav",
                "audio/wave",
                "audio/ogg",
                "audio/flac",
                "audio/aac",
                "audio/m4a",
                "audio/x-m4a"
        };

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(audioRecordRepository.save(any(AudioRecord.class)))
                .thenAnswer(invocation -> {
                    AudioRecord record = invocation.getArgument(0);
                    record.setId(UUID.randomUUID());
                    return record;
                });
        when(processingTaskRepository.save(any(ProcessingTask.class)))
                .thenAnswer(invocation -> {
                    ProcessingTask task = invocation.getArgument(0);
                    task.setId(UUID.randomUUID());
                    return task;
                });
        doNothing().when(audioProcessingProducer).sendProcessingTask(any(UUID.class));

        // Act & Assert - All valid MIME types should be accepted
        for (String mimeType : validMimeTypes) {
            assertDoesNotThrow(() -> audioProcessingService.processUpload(
                    authentication,
                    "audio/test/meeting.mp3",
                    "meeting.mp3",
                    120,
                    mimeType,
                    240000L
            ), "MIME type " + mimeType + " should be valid");
        }
    }
}
