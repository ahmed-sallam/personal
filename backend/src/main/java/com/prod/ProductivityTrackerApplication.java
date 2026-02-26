package com.prod;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Productivity Tracker Backend Application (إنتاجيتي).
 *
 * This Spring Boot application provides a REST API for voice-to-insight tracking,
 * featuring:
 * - Whitelist-based authentication with OTP and JWT
 * - Asynchronous audio processing via RabbitMQ
 * - AI integration with OpenRouter (Whisper for transcription, Claude for analysis)
 * - PostgreSQL database with JSONB support for structured AI metadata
 * - Redis for OTP storage and session management
 * - Real-time status updates via Server-Sent Events (SSE)
 *
 * @author Auto-Claude
 * @version 0.0.1-SNAPSHOT
 */
@SpringBootApplication
public class ProductivityTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductivityTrackerApplication.class, args);
    }
}
