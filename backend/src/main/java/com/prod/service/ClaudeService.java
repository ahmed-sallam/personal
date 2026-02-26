package com.prod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for text analysis and structured data extraction using Claude 3.5 Sonnet.
 *
 * This service handles natural language understanding operations using Claude
 * 3.5 Sonnet through OpenRouter. It analyzes transcribed text from audio
 * recordings and extracts structured data for storage in the database.
 *
 * Primary Functions:
 * - Extract event information from transcriptions
 * - Identify duration and categorize events
 * - Generate action items from discussions
 * - Produce structured JSONB data for database storage
 * - Track token usage for cost monitoring
 *
 * Processing Flow:
 * 1. AudioProcessingService receives transcribed text from WhisperService
 * 2. Calls ClaudeService to analyze the transcription
 * 3. Claude extracts structured data (event, duration, category, action items)
 * 4. Returns Map<String, Object> for storage in ai_summaries.structured_data
 * 5. Action items are extracted and stored separately in action_items table
 *
 * Expected JSONB Output Structure:
 * <pre>{
 *   "event": "Requirements meeting with tech team",
 *   "duration_minutes": 40,
 *   "category": "meeting",
 *   "action_items": [
 *     "Review API specifications",
 *     "Schedule follow-up"
 *   ]
 * }</pre>
 *
 * Language Support:
 * - English and Arabic transcriptions
 * - Multilingual context understanding
 * - Language-agnostic category classification
 *
 * Model: anthropic/claude-3.5-sonnet
 * - 200K context window for long transcriptions
 * - Advanced instruction following
 * - Strong JSON output formatting
 * - Cost-effective for production use
 *
 * Error Handling:
 * - Invalid JSON response: throws RuntimeException with details
 * - Empty transcription: throws IllegalArgumentException
 * - API errors: throws RuntimeException with details
 * - Retry logic: handled by AudioProcessingService (up to 3 retries)
 *
 * @see org.springframework.ai.chat.client.ChatClient
 * @see com.prod.entity.AISummary
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaudeService {

    private final ChatClient claudeClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.anthropic.chat.options.model:anthropic/claude-3.5-sonnet}")
    private String model;

    /**
     * System prompt for Claude to guide its analysis behavior.
     * This instructs Claude to extract specific structured data from
     * transcriptions and return it in a consistent JSON format.
     */
    private static final String SYSTEM_PROMPT = """
            You are an intelligent assistant that analyzes voice recording transcriptions
            to extract structured productivity data. Your task is to identify:

            1. EVENT: A concise summary of what the recording is about (max 100 characters)
            2. DURATION_MINUTES: The duration mentioned or estimated in minutes
            3. CATEGORY: One of: meeting, call, brainstorming, review, planning, learning, other
            4. ACTION_ITEMS: List of specific tasks or commitments mentioned (empty array if none)

            Rules:
            - If duration is not explicitly mentioned, estimate based on content context
            - Categories must be one of the predefined values
            - Action items should be concise and actionable
            - Support both English and Arabic content
            - Return ONLY valid JSON, no additional text or explanations

            Output format (JSON):
            {
              "event": "Brief event description",
              "duration_minutes": <integer>,
              "category": "<category>",
              "action_items": ["item1", "item2"]
            }
            """;

    /**
     * Analyze transcribed text and extract structured data.
     *
     * This method performs the following steps:
     * 1. Validate transcription is not null or empty
     * 2. Create a prompt with the system instruction and transcription
     * 3. Call Claude API for analysis
     * 4. Parse JSON response into structured data
     * 5. Return Map for storage in ai_summaries.structured_data (JSONB column)
     * 6. Log token usage for cost tracking
     *
     * The Claude model will:
     * - Identify the main event/topic from the transcription
     * - Extract or estimate the duration in minutes
     * - Classify the event into a category (meeting, call, brainstorming, etc.)
     * - Extract actionable items as a list
     * - Return data in consistent JSON format
     *
     * @param transcription the transcribed text from WhisperService
     * @return structured data as Map<String, Object> for JSONB storage
     * @throws IllegalArgumentException if transcription is null or empty
     * @throws RuntimeException if analysis fails or JSON parsing fails
     */
    public Map<String, Object> analyzeTranscription(String transcription) {
        // Validate input
        if (transcription == null || transcription.trim().isEmpty()) {
            log.error("Attempted to analyze null or empty transcription");
            throw new IllegalArgumentException("Transcription cannot be null or empty");
        }

        log.info("Starting transcription analysis using Claude model");
        log.debug("Transcription length: {} characters", transcription.length());

        try {
            // Create prompt with system instruction and user content
            Prompt prompt = new Prompt(SYSTEM_PROMPT);
            ChatResponse response = claudeClient.prompt(prompt)
                    .user(transcription)
                    .call()
                    .chatResponse();

            // Extract token usage for monitoring
            var usage = response.getMetadata().getUsage();
            long promptTokens = usage.getPromptTokens();
            long generationTokens = usage.getGenerationTokens();
            long totalTokens = usage.getTotalTokens();

            log.debug("Token usage - prompt: {}, generation: {}, total: {}",
                    promptTokens, generationTokens, totalTokens);

            // Extract JSON response from Claude
            String jsonResponse = response.getResult().getOutput().getContent();

            // Validate response is not empty
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                log.warn("Claude returned empty response for transcription analysis");
                throw new RuntimeException(
                        "Text analysis failed: No structured data was generated. The transcription may be too short or unclear."
                );
            }

            log.debug("Raw JSON response from Claude: {}", jsonResponse);

            // Parse JSON response into Map
            Map<String, Object> structuredData = parseJsonResponse(jsonResponse);

            // Validate required fields exist
            validateStructuredData(structuredData);

            log.info("Transcription analysis completed successfully");
            log.debug("Extracted event: {}, category: {}, duration: {} minutes, action items: {}",
                    structuredData.get("event"),
                    structuredData.get("category"),
                    structuredData.get("duration_minutes"),
                    ((Object[]) structuredData.getOrDefault("action_items", new Object[0])).length);

            return structuredData;

        } catch (IllegalArgumentException e) {
            // Re-throw validation exceptions
            throw e;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON response from Claude: {}", e.getMessage());
            throw new RuntimeException(
                    "Text analysis failed: Invalid JSON response from AI model. " + e.getMessage(),
                    e
            );
        } catch (Exception e) {
            // Wrap all other exceptions in RuntimeException
            log.error("Failed to analyze transcription: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "Text analysis failed: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Parse JSON response string into Map<String, Object>.
     *
     * This method uses Jackson ObjectMapper to deserialize the JSON string
     * returned by Claude into a Map structure compatible with JSONB storage.
     *
     * @param jsonResponse the JSON string from Claude
     * @return parsed data as Map<String, Object>
     * @throws JsonProcessingException if JSON is malformed
     */
    private Map<String, Object> parseJsonResponse(String jsonResponse) throws JsonProcessingException {
        // Clean up response - remove markdown code blocks if present
        String cleanedJson = jsonResponse.trim();
        if (cleanedJson.startsWith("```json")) {
            cleanedJson = cleanedJson.substring(7);
        } else if (cleanedJson.startsWith("```")) {
            cleanedJson = cleanedJson.substring(3);
        }
        if (cleanedJson.endsWith("```")) {
            cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
        }
        cleanedJson = cleanedJson.trim();

        return objectMapper.readValue(cleanedJson, new TypeReference<>() {});
    }

    /**
     * Validate that the structured data contains all required fields.
     *
     * Required fields:
     * - event (String): Event description
     * - duration_minutes (Integer): Duration in minutes
     * - category (String): Event category
     * - action_items (Array): List of action items
     *
     * @param structuredData the parsed structured data
     * @throws IllegalArgumentException if required fields are missing
     */
    private void validateStructuredData(Map<String, Object> structuredData) {
        if (!structuredData.containsKey("event")) {
            throw new RuntimeException("Invalid structured data: missing 'event' field");
        }
        if (!structuredData.containsKey("duration_minutes")) {
            throw new RuntimeException("Invalid structured data: missing 'duration_minutes' field");
        }
        if (!structuredData.containsKey("category")) {
            throw new RuntimeException("Invalid structured data: missing 'category' field");
        }
        if (!structuredData.containsKey("action_items")) {
            throw new RuntimeException("Invalid structured data: missing 'action_items' field");
        }

        // Validate field types
        if (!(structuredData.get("event") instanceof String)) {
            throw new RuntimeException("Invalid structured data: 'event' must be a string");
        }
        if (!(structuredData.get("category") instanceof String)) {
            throw new RuntimeException("Invalid structured data: 'category' must be a string");
        }
        // duration_minutes can be Integer or String (we'll convert later)
        // action_items must be a list
        if (!(structuredData.get("action_items") instanceof Iterable)) {
            throw new RuntimeException("Invalid structured data: 'action_items' must be an array");
        }

        // Validate category is one of the allowed values
        String category = (String) structuredData.get("category");
        String[] validCategories = {"meeting", "call", "brainstorming", "review", "planning", "learning", "other"};
        boolean isValidCategory = false;
        for (String valid : validCategories) {
            if (valid.equalsIgnoreCase(category)) {
                isValidCategory = true;
                break;
            }
        }
        if (!isValidCategory) {
            log.warn("Unknown category '{}' detected, defaulting to 'other'", category);
            structuredData.put("category", "other");
        }

        // Normalize duration_minutes to Integer
        Object duration = structuredData.get("duration_minutes");
        if (duration instanceof String) {
            try {
                structuredData.put("duration_minutes", Integer.parseInt((String) duration));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid structured data: 'duration_minutes' must be a valid integer");
            }
        } else if (duration instanceof Number) {
            structuredData.put("duration_minutes", ((Number) duration).intValue());
        } else {
            throw new RuntimeException("Invalid structured data: 'duration_minutes' must be an integer");
        }
    }

    /**
     * Get the model identifier used for text analysis.
     *
     * @return the model name (e.g., "anthropic/claude-3.5-sonnet")
     */
    public String getModel() {
        return model;
    }

    /**
     * Get a summary of structured data for logging purposes.
     *
     * This method returns a human-readable summary of the structured data
     * without including sensitive or verbose content.
     *
     * @param structuredData the structured data map
     * @return summary string for logging
     */
    public String getDataSummary(Map<String, Object> structuredData) {
        if (structuredData == null) {
            return "No data";
        }
        return String.format(
                "event='%s', category='%s', duration=%s min, items=%d",
                structuredData.get("event"),
                structuredData.get("category"),
                structuredData.get("duration_minutes"),
                ((Object[]) structuredData.getOrDefault("action_items", new Object[0])).length
        );
    }
}
