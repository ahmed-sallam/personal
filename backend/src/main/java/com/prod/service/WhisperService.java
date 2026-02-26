package com.prod.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Service for speech-to-text transcription using OpenAI Whisper model.
 *
 * This service handles audio transcription operations using the Whisper Large V3
 * model through OpenRouter. It converts audio recordings into text format that
 * can be further analyzed by Claude for structured data extraction.
 *
 * Supported Audio Formats:
 * - MP3, MP4, MPEG, MPGA, M4A, WAV, WEBM
 * - Maximum file size: 25 MB
 * - Maximum duration: No hard limit (practically limited by API timeout)
 *
 * Language Support:
 * - Supports 96 languages including English and Arabic
 * - Auto-detects language by default
 * - Can be explicitly configured for specific languages
 *
 * Usage Flow:
 * 1. Audio file is uploaded via AudioController
 * 2. File metadata is stored in database
 * 3. AudioProcessingService calls this service for transcription
 * 4. Transcribed text is returned and stored in ai_summaries.content
 * 5. ClaudeService analyzes the transcription for structured data
 *
 * Error Handling:
 * - Invalid audio format: throws RuntimeException
 * - Empty/corrupt audio: throws RuntimeException
 * - API errors: throws RuntimeException with details
 * - Retry logic: handled by AudioProcessingService (up to 3 retries)
 *
 * @see org.springframework.ai.openai.OpenAiAudioTranscriptionModel
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhisperService {

    private final OpenAiAudioTranscriptionModel transcriptionModel;

    @Value("${spring.ai.openai.audio.transcription.language:en}")
    private String defaultLanguage;

    @Value("${spring.ai.openai.audio.transcription.prompt:}")
    private String transcriptionPrompt;

    /**
     * Transcribe audio file to text using Whisper model.
     *
     * This method performs the following steps:
     * 1. Validate audio resource is not null
     * 2. Create transcription options with language and prompt
     * 3. Call Whisper API for transcription
     * 4. Extract and return transcribed text
     * 5. Log transcription metadata (duration, language detected)
     *
     * The Whisper model automatically:
     * - Detects the language spoken in the audio
     * - Handles multiple speakers and overlapping speech
     * - Removes filler words and normalizes text
     * - Adds punctuation and capitalization
     *
     * @param audioResource the audio file as a Spring Resource
     * @return the transcribed text
     * @throws IllegalArgumentException if audioResource is null
     * @throws RuntimeException if transcription fails
     */
    public String transcribe(Resource audioResource) {
        // Validate input
        if (audioResource == null) {
            log.error("Attempted to transcribe null audio resource");
            throw new IllegalArgumentException("Audio resource cannot be null");
        }

        log.info("Starting audio transcription using Whisper model");
        log.debug("Audio resource filename: {}", audioResource.getFilename());

        try {
            // Build transcription options
            OpenAiAudioTranscriptionOptions.Builder optionsBuilder = OpenAiAudioTranscriptionOptions.builder()
                    .withLanguage(defaultLanguage);

            // Add prompt if configured (improves transcription accuracy)
            if (transcriptionPrompt != null && !transcriptionPrompt.isEmpty()) {
                optionsBuilder.withPrompt(transcriptionPrompt);
                log.debug("Using transcription prompt: {}", transcriptionPrompt);
            }

            OpenAiAudioTranscriptionOptions options = optionsBuilder.build();

            // Create transcription prompt
            AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource, options);

            // Perform transcription
            log.debug("Calling Whisper API for transcription (language={})", defaultLanguage);
            String transcription = transcriptionModel.call(prompt).getResult().getOutput();

            // Validate transcription result
            if (transcription == null || transcription.trim().isEmpty()) {
                log.warn("Whisper returned empty transcription for audio file");
                throw new RuntimeException(
                        "Audio transcription failed: No text was generated. The audio file may be empty or corrupted."
                );
            }

            log.info("Audio transcription completed successfully");
            log.debug("Transcription length: {} characters", transcription.length());

            return transcription;

        } catch (IllegalArgumentException e) {
            // Re-throw validation exceptions
            throw e;
        } catch (Exception e) {
            // Wrap all other exceptions in RuntimeException
            log.error("Failed to transcribe audio file: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "Audio transcription failed: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Transcribe audio file to text with explicit language specification.
     *
     * This method allows overriding the default language setting for a specific
     * transcription request. Useful when the language is known in advance or
     * when dealing with multilingual audio.
     *
     * Supported language codes (ISO 639-1):
     * - en: English
     * - ar: Arabic
     * - es: Spanish
     * - fr: French
     * - de: German
     * - And 90+ others
     *
     * @param audioResource the audio file as a Spring Resource
     * @param language the language code (e.g., "en", "ar", "es")
     * @return the transcribed text
     * @throws IllegalArgumentException if audioResource is null or language is invalid
     * @throws RuntimeException if transcription fails
     */
    public String transcribeWithLanguage(Resource audioResource, String language) {
        if (audioResource == null) {
            log.error("Attempted to transcribe null audio resource with language");
            throw new IllegalArgumentException("Audio resource cannot be null");
        }
        if (language == null || language.trim().isEmpty()) {
            log.error("Attempted to transcribe with null or empty language code");
            throw new IllegalArgumentException("Language code cannot be null or empty");
        }

        log.info("Starting audio transcription with explicit language: {}", language);

        try {
            // Build transcription options with specified language
            OpenAiAudioTranscriptionOptions.Builder optionsBuilder = OpenAiAudioTranscriptionOptions.builder()
                    .withLanguage(language);

            // Add prompt if configured
            if (transcriptionPrompt != null && !transcriptionPrompt.isEmpty()) {
                optionsBuilder.withPrompt(transcriptionPrompt);
            }

            OpenAiAudioTranscriptionOptions options = optionsBuilder.build();

            // Create transcription prompt
            AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource, options);

            // Perform transcription
            String transcription = transcriptionModel.call(prompt).getResult().getOutput();

            // Validate transcription result
            if (transcription == null || transcription.trim().isEmpty()) {
                log.warn("Whisper returned empty transcription for audio file (language: {})", language);
                throw new RuntimeException(
                        "Audio transcription failed: No text was generated. The audio file may be empty or corrupted."
                );
            }

            log.info("Audio transcription completed successfully (language: {})", language);
            log.debug("Transcription length: {} characters", transcription.length());

            return transcription;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to transcribe audio file with language {}: {}", language, e.getMessage(), e);
            throw new RuntimeException(
                    "Audio transcription failed: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Check if an audio file format is supported by Whisper.
     *
     * This method validates the file extension against supported formats.
     * It can be used to provide early validation before attempting transcription.
     *
     * Supported formats: mp3, mp4, mpeg, mpga, m4a, wav, webm
     *
     * @param filename the name of the audio file
     * @return true if the format is supported, false otherwise
     */
    public boolean isSupportedFormat(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        String lowercaseName = filename.toLowerCase();
        return lowercaseName.endsWith(".mp3") ||
                lowercaseName.endsWith(".mp4") ||
                lowercaseName.endsWith(".mpeg") ||
                lowercaseName.endsWith(".mpga") ||
                lowercaseName.endsWith(".m4a") ||
                lowercaseName.endsWith(".wav") ||
                lowercaseName.endsWith(".webm");
    }

    /**
     * Get the default language code used for transcription.
     *
     * @return the default language code (e.g., "en", "ar")
     */
    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    /**
     * Get the transcription prompt currently configured.
     *
     * The prompt is used to guide the transcription model and can improve
     * accuracy for specific domains or vocabularies.
     *
     * @return the transcription prompt, or empty string if not configured
     */
    public String getTranscriptionPrompt() {
        return transcriptionPrompt;
    }
}
