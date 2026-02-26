package com.prod.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Producer for sending audio processing tasks to RabbitMQ queue.
 *
 * This class handles the publishing of audio processing tasks to the RabbitMQ
 * message broker for asynchronous processing. When a user uploads an audio file,
 * the metadata is saved to the database and the audio record ID is sent to the
 * queue for background processing.
 *
 * Architecture Flow:
 * 1. User uploads audio file via AudioController
 * 2. AudioProcessingService saves metadata to database
 * 3. AudioProcessingProducer sends audio record ID to RabbitMQ
 * 4. AudioProcessingConsumer receives the message and processes:
 *    - Calls WhisperService for transcription
 *    - Calls ClaudeService for analysis
 *    - Saves AISummary to database
 *    - Updates ProcessingTask status
 *
 * Benefits:
 * - Non-blocking: HTTP request returns immediately after queuing
 * - Scalability: Multiple consumers can process tasks in parallel
 * - Reliability: Failed messages go to DLQ for analysis
 * - Decoupling: Upload and processing are independent
 *
 * Message Format:
 * - Payload: UUID (audio record ID as string)
 * - Exchange: audio.exchange (direct)
 * - Routing Key: audio.process
 * - Queue: audio.processing.queue
 *
 * Error Handling:
 * - Publisher confirms log success/failure
 * - Returns callback logs unroutable messages
 * - Failed messages go to DLQ after retry attempts
 *
 * @see org.springframework.amqp.rabbit.core.RabbitTemplate
 * @see com.prod.config.RabbitMQConfig
 * @see com.prod.messaging.AudioProcessingConsumer
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AudioProcessingProducer {

    private final RabbitTemplate rabbitTemplate;

    // Exchange and routing key configured in application.yml
    @Value("${app.rabbitmq.exchange.audio:audio.exchange}")
    private String audioExchange;

    @Value("${app.rabbitmq.routing-key.audio:audio.process}")
    private String audioRoutingKey;

    /**
     * Send an audio processing task to the RabbitMQ queue.
     *
     * This method publishes the audio record ID to the audio processing queue.
     * The message will be consumed by AudioProcessingConsumer for asynchronous
     * processing (transcription and analysis).
     *
     * The message is sent using:
     * - Exchange: audio.exchange (direct exchange for precise routing)
     * - Routing Key: audio.process
     * - Message Converter: Jackson2JsonMessageConverter (automatic UUID serialization)
     *
     * Publisher Confirms:
     * - Success: Logged at DEBUG level
     * - Failure: Logged at ERROR level with cause
     *
     * Returns Callback:
     * - Unroutable messages are logged at ERROR level
     *
     * @param audioRecordId the UUID of the audio record to process
     * @throws IllegalArgumentException if audioRecordId is null
     */
    public void sendProcessingTask(UUID audioRecordId) {
        // Validate input
        if (audioRecordId == null) {
            log.error("Attempted to send null audio record ID to processing queue");
            throw new IllegalArgumentException("Audio record ID cannot be null");
        }

        log.info("Sending audio processing task to queue: audioRecordId={}", audioRecordId);
        log.debug("Queue configuration: exchange={}, routingKey={}", audioExchange, audioRoutingKey);

        try {
            // Send message to RabbitMQ
            // The Jackson2JsonMessageConverter will automatically serialize the UUID to JSON
            rabbitTemplate.convertAndSend(
                    audioExchange,
                    audioRoutingKey,
                    audioRecordId
            );

            log.info("Audio processing task queued successfully: audioRecordId={}", audioRecordId);

        } catch (Exception e) {
            // Log error with context for debugging
            log.error("Failed to send audio processing task to queue: audioRecordId={}, error={}",
                    audioRecordId, e.getMessage(), e);
            throw e; // Re-throw to allow caller to handle
        }
    }

    /**
     * Send an audio processing task with additional priority information.
     *
     * This overload allows specifying a priority for the audio processing task.
     * Higher priority tasks will be processed before lower priority tasks when
     * using priority queues (future enhancement).
     *
     * Currently, priority is logged but not used in queue configuration.
     * To enable priority queues, configure x-max-priority in RabbitMQConfig.
     *
     * @param audioRecordId the UUID of the audio record to process
     * @param priority the priority level (higher = more important, 0-10)
     * @throws IllegalArgumentException if audioRecordId is null
     */
    public void sendProcessingTask(UUID audioRecordId, int priority) {
        // Validate input
        if (audioRecordId == null) {
            log.error("Attempted to send null audio record ID to processing queue");
            throw new IllegalArgumentException("Audio record ID cannot be null");
        }

        log.info("Sending audio processing task to queue with priority: audioRecordId={}, priority={}",
                audioRecordId, priority);

        try {
            // Send message to RabbitMQ
            // Priority can be used in future with x-max-priority queue configuration
            rabbitTemplate.convertAndSend(
                    audioExchange,
                    audioRoutingKey,
                    audioRecordId,
                    message -> {
                        // Set priority message property (requires priority queue configuration)
                        message.getMessageProperties().setPriority(priority);
                        log.debug("Message priority set to: {}", priority);
                        return message;
                    }
            );

            log.info("Audio processing task queued successfully with priority: audioRecordId={}, priority={}",
                    audioRecordId, priority);

        } catch (Exception e) {
            log.error("Failed to send audio processing task to queue: audioRecordId={}, priority={}, error={}",
                    audioRecordId, priority, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the current exchange name for audio processing messages.
     *
     * This method is primarily used for logging and testing purposes.
     *
     * @return the exchange name (default: "audio.exchange")
     */
    public String getExchange() {
        return audioExchange;
    }

    /**
     * Get the current routing key for audio processing messages.
     *
     * This method is primarily used for logging and testing purposes.
     *
     * @return the routing key (default: "audio.process")
     */
    public String getRoutingKey() {
        return audioRoutingKey;
    }
}
