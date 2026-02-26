package com.prod.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI configuration for OpenRouter ChatClient beans.
 *
 * This class configures ChatClient instances for interacting with AI models
 * through OpenRouter, which acts as a unified gateway for multiple AI providers.
 * It sets up two primary chat models:
 *
 * 1. Whisper (OpenAI) - Speech-to-text transcription for audio recordings
 * 2. Claude 3.5 Sonnet (Anthropic) - Text analysis and structured data extraction
 *
 * Configuration Details:
 * - Base URL: OpenRouter API endpoint (https://openrouter.ai/api/v1)
 * - Whisper Model: openai/whisper-large-v3 (audio transcription)
 * - Claude Model: anthropic/claude-3.5-sonnet (text analysis)
 * - Authentication: API keys injected from environment variables
 *
 * Usage:
 * <pre>{@code
 * @Autowired
 * private ChatClient whisperClient;
 *
 * @Autowired
 * private ChatClient claudeClient;
 *
 * // Transcribe audio
 * String transcription = whisperClient.prompt()
 *     .user(audioContent)
 *     .call()
 *     .content();
 *
 * // Analyze text
 * String analysis = claudeClient.prompt()
 *     .user("Extract structured data from: " + transcription)
 *     .call()
 *     .content();
 * }</pre>
 *
 * OpenRouter Integration:
 * - OpenRouter acts as a unified API gateway for multiple AI providers
 * - Single API key can be used for both OpenAI and Anthropic models
 * - Base URL is overridden to point to OpenRouter's endpoint
 * - Model identifiers include provider prefix (openai/, anthropic/)
 *
 * Token Tracking:
 * - Token usage can be tracked via ChatResponse metadata
 * - Use .call().chatResponse().metadata().usage() to get token counts
 * - Important for cost monitoring and rate limit management
 *
 * @see org.springframework.ai.chat.client.ChatClient
 * @see org.springframework.ai.openai.OpenAiChatModel
 * @see org.springframework.ai.anthropic.AnthropicChatModel
 */
@Configuration
@Slf4j
public class SpringAIConfig {

    private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${spring.ai.anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${spring.ai.openai.chat.options.model:openai/whisper-large-v3}")
    private String whisperModel;

    @Value("${spring.ai.anthropic.chat.options.model:anthropic/claude-3.5-sonnet}")
    private String claudeModel;

    /**
     * Configure OpenAI ChatModel for Whisper transcription via OpenRouter.
     *
     * This bean creates an OpenAiChatModel configured to use OpenRouter's
     * Whisper Large V3 model for speech-to-text transcription. OpenRouter
     * acts as a gateway, allowing us to use OpenAI models through a single
     * unified endpoint.
     *
     * Model: openai/whisper-large-v3
     * - Supports multiple languages (English, Arabic, and 96 others)
     * - Handles various audio formats (MP3, WAV, M4A, etc.)
     * - Returns timestamped transcriptions
     * - Robust to background noise and overlapping speech
     *
     * @return configured OpenAiChatModel for Whisper transcription
     */
    @Bean
    public OpenAiChatModel whisperChatModel() {
        log.info("Configuring Whisper ChatModel: model={}, baseUrl={}", whisperModel, OPENROUTER_BASE_URL);

        OpenAiApi openAiApi = new OpenAiApi(OPENROUTER_BASE_URL, openAiApiKey);

        // Create options using the static creation method
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(whisperModel)
                .build();

        return new OpenAiChatModel(openAiApi, options);
    }

    /**
     * Configure ChatClient for Whisper transcription operations.
     *
     * The ChatClient provides a fluent API for interacting with the Whisper model.
     * It is the primary interface for audio transcription operations in the
     * AudioProcessingService.
     *
     * Usage Example:
     * <pre>{@code
     * String transcription = whisperClient.prompt()
     *     .user(audioFileBytes)
     *     .call()
     *     .content();
     * }</pre>
     *
     * @param whisperChatModel the configured Whisper chat model
     * @return ChatClient for Whisper transcription
     */
    @Bean
    public ChatClient whisperClient(OpenAiChatModel whisperChatModel) {
        log.debug("Creating Whisper ChatClient bean");
        return ChatClient.create(whisperChatModel);
    }

    /**
     * Configure OpenAI Audio Transcription Model for Whisper via OpenRouter.
     *
     * This bean creates an OpenAiAudioTranscriptionModel specifically designed
     * for audio-to-text transcription using the Whisper model. Unlike the chat
     * model, this audio transcription model handles audio files directly and
     * returns transcribed text.
     *
     * Model: openai/whisper-large-v3
     * - Supports 96+ languages (English, Arabic, Spanish, etc.)
     * - Handles formats: MP3, MP4, MPEG, MPGA, M4A, WAV, WEBM
     * - Auto-detects language and adds punctuation
     * - Maximum file size: 25 MB
     *
     * Usage Example in WhisperService:
     * <pre>{@code
     * OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
     *     .withLanguage("en")
     *     .build();
     *
     * AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource, options);
     * String transcription = transcriptionModel.call(prompt).getResult().getOutput();
     * }</pre>
     *
     * @return configured OpenAiAudioTranscriptionModel for audio transcription
     * @see com.prod.service.WhisperService
     */
    @Bean
    public OpenAiAudioTranscriptionModel whisperTranscriptionModel() {
        log.info("Configuring Whisper Audio Transcription Model: baseUrl={}", OPENROUTER_BASE_URL);

        // Use the single argument constructor (apiKey only)
        // The baseUrl should be configured via application.properties
        // spring.ai.openai.base-url=https://openrouter.ai/api/v1
        OpenAiAudioApi openAiAudioApi = new OpenAiAudioApi(openAiApiKey);

        // Create default options for audio transcription
        OpenAiAudioTranscriptionOptions defaultOptions = OpenAiAudioTranscriptionOptions.builder()
                .build();

        return new OpenAiAudioTranscriptionModel(openAiAudioApi, defaultOptions);
    }

    /**
     * Configure Anthropic ChatModel for Claude 3.5 Sonnet via OpenRouter.
     *
     * This bean creates an AnthropicChatModel configured to use OpenRouter's
     * Claude 3.5 Sonnet model for advanced text analysis and structured
     * data extraction. Claude is used for:
     *
     * - Extracting event information from transcriptions
     * - Identifying categories and tags
     * - Generating action items
     * - Producing structured JSONB data for database storage
     *
     * Model: anthropic/claude-3.5-sonnet
     * - Advanced natural language understanding
     * - Excellent at following complex instructions
     * - Strong JSON output formatting
     * - Handles multilingual text (English and Arabic)
     * - Cost-effective with 200K context window
     *
     * @return configured AnthropicChatModel for Claude analysis
     */
    @Bean
    public AnthropicChatModel claudeChatModel() {
        log.info("Configuring Claude ChatModel: model={}, baseUrl={}", claudeModel, OPENROUTER_BASE_URL);

        AnthropicApi anthropicApi = new AnthropicApi(OPENROUTER_BASE_URL, anthropicApiKey);

        // Create options using the static creation method
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .withModel(claudeModel)
                .build();

        return new AnthropicChatModel(anthropicApi, options);
    }

    /**
     * Configure ChatClient for Claude analysis operations.
     *
     * The ChatClient provides a fluent API for interacting with the Claude model.
     * It is the primary interface for text analysis operations in the
     * ClaudeService.
     *
     * Usage Example:
     * <pre>{@code
     * String structuredData = claudeClient.prompt()
     *     .user("Extract event, duration, category from: " + transcription)
     *     .call()
     *     .content();
     * }</pre>
     *
     * Token Usage Tracking:
     * <pre>{@code
     * var response = claudeClient.prompt()
     *     .user(prompt)
     *     .call();
     *
     * ChatResponse chatResponse = response.chatResponse();
     * Usage usage = chatResponse.metadata().usage();
     * log.info("Tokens used: prompt={}, completion={}, total={}",
     *     usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
     * }</pre>
     *
     * @param claudeChatModel the configured Claude chat model
     * @return ChatClient for Claude text analysis
     */
    @Bean
    public ChatClient claudeClient(AnthropicChatModel claudeChatModel) {
        log.debug("Creating Claude ChatClient bean");
        return ChatClient.create(claudeChatModel);
    }
}
