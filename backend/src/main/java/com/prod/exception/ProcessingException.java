package com.prod.exception;

/**
 * Exception thrown when audio processing fails.
 *
 * This exception is used when the asynchronous audio processing pipeline
 * encounters errors during transcription (Whisper) or analysis (Claude).
 * It differs from general business exceptions in that it specifically relates
 * to the AI/ML processing pipeline.
 *
 * Usage examples:
 * - Whisper transcription fails due to invalid audio format
 * - Claude API rate limit exceeded
 * - Network timeout when calling OpenRouter API
 * - Invalid or empty audio file
 * - RabbitMQ queue unavailable
 *
 * GlobalExceptionHandler maps this to HTTP 500 Internal Server Error
 * (or 503 Service Unavailable for infrastructure issues) with RFC 7807 format.
 *
 * @see com.prod.service.WhisperService
 * @see com.prod.service.ClaudeService
 * @see com.prod.messaging.AudioProcessingConsumer
 * @see com.prod.exception.GlobalExceptionHandler
 */
public class ProcessingException extends RuntimeException {

    private final String processingStage;
    private final String errorCode;

    /**
     * Constructs a new ProcessingException with the specified detail message.
     *
     * @param message the detail message explaining the processing failure
     */
    public ProcessingException(String message) {
        super(message);
        this.processingStage = null;
        this.errorCode = null;
    }

    /**
     * Constructs a new ProcessingException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.processingStage = null;
        this.errorCode = null;
    }

    /**
     * Constructs a new ProcessingException with stage, error code, message, and cause.
     *
     * @param processingStage the stage where processing failed (e.g., "transcription", "analysis")
     * @param errorCode the error code for categorization (e.g., "INVALID_AUDIO", "API_TIMEOUT")
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public ProcessingException(String processingStage, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.processingStage = processingStage;
        this.errorCode = errorCode;
    }

    /**
     * Gets the processing stage where the error occurred.
     *
     * @return the processing stage, or null if not specified
     */
    public String getProcessingStage() {
        return processingStage;
    }

    /**
     * Gets the error code for categorization.
     *
     * @return the error code, or null if not specified
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Constructs a new ProcessingException for transcription failures.
     *
     * @param audioId the ID of the audio file being transcribed
     * @param cause the cause of the transcription failure
     * @return a ProcessingException with a formatted message
     */
    public static ProcessingException transcriptionFailed(String audioId, Throwable cause) {
        return new ProcessingException(
                "transcription",
                "TRANSCRIPTION_FAILED",
                String.format("Failed to transcribe audio file '%s'. The audio format may be unsupported or the file is corrupted.", audioId),
                cause
        );
    }

    /**
     * Constructs a new ProcessingException for analysis failures.
     *
     * @param audioId the ID of the audio record being analyzed
     * @param cause the cause of the analysis failure
     * @return a ProcessingException with a formatted message
     */
    public static ProcessingException analysisFailed(String audioId, Throwable cause) {
        return new ProcessingException(
                "analysis",
                "ANALYSIS_FAILED",
                String.format("Failed to analyze transcription for audio record '%s'. The AI service may be unavailable or rate limited.", audioId),
                cause
        );
    }

    /**
     * Constructs a new ProcessingException for invalid audio files.
     *
     * @param fileName the name of the invalid audio file
     * @param reason the reason for invalidity (e.g., "unsupported format", "file too large")
     * @return a ProcessingException with a formatted message
     */
    public static ProcessingException invalidAudio(String fileName, String reason) {
        return new ProcessingException(
                "validation",
                "INVALID_AUDIO",
                String.format("Invalid audio file '%s': %s", fileName, reason),
                null
        );
    }

    /**
     * Constructs a new ProcessingException for queue unavailability.
     *
     * @return a ProcessingException with a formatted message
     */
    public static ProcessingException queueUnavailable() {
        return new ProcessingException(
                "queue",
                "QUEUE_UNAVAILABLE",
                "Audio processing queue is unavailable. The system may be under heavy load. Please try again later.",
                null
        );
    }

    /**
     * Constructs a new ProcessingException for API rate limiting.
     *
     * @param service the service that rate limited the request (e.g., "OpenRouter", "Whisper")
     * @return a ProcessingException with a formatted message
     */
    public static ProcessingException rateLimitExceeded(String service) {
        return new ProcessingException(
                "api",
                "RATE_LIMIT_EXCEEDED",
                String.format("Rate limit exceeded for %s API. Please wait before trying again.", service),
                null
        );
    }

    /**
     * Constructs a new ProcessingException for API timeout.
     *
     * @param service the service that timed out (e.g., "Whisper", "Claude")
     * @param cause the cause of the timeout
     * @return a ProcessingException with a formatted message
     */
    public static ProcessingException apiTimeout(String service, Throwable cause) {
        return new ProcessingException(
                "api",
                "API_TIMEOUT",
                String.format("Request to %s API timed out. The service may be experiencing issues.", service),
                cause
        );
    }

    /**
     * Constructs a new ProcessingException for empty transcription.
     *
     * @param audioId the ID of the audio file that resulted in empty transcription
     * @return a ProcessingException with a formatted message
     */
    public static ProcessingException emptyTranscription(String audioId) {
        return new ProcessingException(
                "transcription",
                "EMPTY_TRANSCRIPTION",
                String.format("Transcription resulted in empty text for audio file '%s'. The audio may be silent or contain no speech.", audioId),
                null
        );
    }
}
