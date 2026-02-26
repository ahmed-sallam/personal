package com.prod.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for asynchronous audio processing.
 *
 * This class configures the RabbitMQ message broker infrastructure for handling
 * audio processing tasks asynchronously. It sets up exchanges, queues, and bindings
 * following a direct exchange pattern with dead letter queue support for failed messages.
 *
 * Architecture:
 * - Exchange: Direct exchange for routing messages to specific queues
 * - Main Queue: audio.processing.queue (holds audio processing tasks)
 * - DLQ: audio.processing.dlq (holds failed messages for analysis/retry)
 * - Routing Key: audio.process (routes messages to audio processing queue)
 *
 * Features:
 * - JSON message converter for serialization
 * - Dead Letter Queue (DLQ) for failed message handling
 * - TTL and max-length settings for queue management
 * - Publisher confirms for reliable message delivery
 * - Configurable exchange, queue, and routing key names
 *
 * @see org.springframework.amqp.core.Queue
 * @see org.springframework.amqp.core.DirectExchange
 * @see org.springframework.amqp.core.Binding
 * @see org.springframework.amqp.rabbit.core.RabbitTemplate
 */
@Configuration
@Slf4j
public class RabbitMQConfig {

    // Exchange Configuration
    @Value("${app.rabbitmq.exchange.audio:audio.exchange}")
    private String audioExchange;

    // Queue Configuration
    @Value("${app.rabbitmq.queue.audio.processing:audio.processing.queue}")
    private String audioProcessingQueue;

    @Value("${app.rabbitmq.queue.audio.dlq:audio.processing.dlq}")
    private String audioProcessingDLQ;

    // Routing Key Configuration
    @Value("${app.rabbitmq.routing-key.audio:audio.process}")
    private String audioRoutingKey;

    @Value("${app.rabbitmq.routing-key.dlq:audio.process.dlq}")
    private String dlqRoutingKey;

    // Queue Configuration Properties
    @Value("${app.rabbitmq.queue.ttl:86400000}") // 24 hours in milliseconds
    private long queueTTL;

    @Value("${app.rabbitmq.queue.max-length:1000}")
    private int queueMaxLength;

    /**
     * Configure JSON message converter for RabbitMQ.
     *
     * This converter serializes Java objects to JSON format before sending to RabbitMQ
     * and deserializes JSON messages back to Java objects when consuming. This enables
     * type-safe message passing between producers and consumers.
     *
     * @return Jackson2JsonMessageConverter for JSON serialization
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        log.debug("Configuring Jackson2JsonMessageConverter for RabbitMQ");
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configure RabbitTemplate with JSON message converter.
     *
     * The RabbitTemplate is the main helper class for sending and receiving messages.
     * It's configured with a JSON converter for automatic serialization/deserialization
     * of message objects.
     *
     * @param connectionFactory the RabbitMQ connection factory
     * @return configured RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());

        // Enable publisher confirms for reliable message delivery
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("Message published successfully to RabbitMQ");
            } else {
                log.error("Failed to publish message to RabbitMQ: {}", cause);
            }
        });

        // Enable returns for unroutable messages
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("Message returned from RabbitMQ - Exchange: {}, RoutingKey: {}, ReplyText: {}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyText());
        });

        log.info("RabbitTemplate configured with JSON message converter and publisher confirms");
        return rabbitTemplate;
    }

    /**
     * Configure Dead Letter Queue (DLQ) for failed audio processing messages.
     *
     * The DLQ holds messages that failed processing after all retry attempts.
     * Messages in DLQ can be analyzed to identify failure patterns and manually
     * replayed if necessary.
     *
     * DLQ Features:
     * - durable: Survives broker restart
     * - exclusive: Not exclusive to one connection
     * - auto-delete: Not automatically deleted when no consumers
     *
     * @return Dead Letter Queue for failed audio processing messages
     */
    @Bean
    public Queue audioProcessingDLQ() {
        log.info("Configuring DLQ: {} (durable=true)", audioProcessingDLQ);
        return QueueBuilder.durable(audioProcessingDLQ)
                .withArgument("x-dead-letter-exchange", audioExchange)
                .withArgument("x-dead-letter-routing-key", audioRoutingKey)
                .build();
    }

    /**
     * Configure main audio processing queue.
     *
     * This queue holds audio processing tasks waiting to be processed by consumers.
     * Messages are sent to this queue by the AudioProcessingProducer and consumed
     * by the AudioProcessingConsumer.
     *
     * Queue Features:
     * - durable: Survives broker restart
     * - TTL: Messages expire after configured time (default: 24 hours)
     * - Max Length: Queue rejects new messages when limit reached (default: 1000)
     * - DLQ: Failed messages are routed to DLQ for analysis
     *
     * @return Audio processing queue with DLQ configuration
     */
    @Bean
    public Queue audioProcessingQueue() {
        log.info("Configuring queue: {} (durable=true, ttl={}, maxLength={})",
                audioProcessingQueue, queueTTL, queueMaxLength);

        return QueueBuilder.durable(audioProcessingQueue)
                .withArgument("x-message-ttl", queueTTL)
                .withArgument("x-max-length", queueMaxLength)
                .withArgument("x-dead-letter-exchange", audioExchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    /**
     * Configure direct exchange for audio processing.
     *
     * A direct exchange routes messages to queues based on an exact routing key match.
     * This is appropriate for our use case as we need precise control over message routing.
     *
     * @return Direct exchange for audio processing
     */
    @Bean
    public DirectExchange audioExchange() {
        log.info("Configuring direct exchange: {} (durable=true)", audioExchange);
        return new DirectExchange(audioExchange, true, false);
    }

    /**
     * Configure binding between exchange and DLQ.
     *
     * This binding routes messages with the DLQ routing key to the dead letter queue.
     * Messages that fail processing (after retries) are routed here via x-dead-letter-routing-key.
     *
     * @return Binding for DLQ
     */
    @Bean
    public Binding dlqBinding() {
        log.debug("Binding DLQ {} to exchange {} with routing key {}",
                audioProcessingDLQ, audioExchange, dlqRoutingKey);

        return BindingBuilder
                .bind(audioProcessingDLQ())
                .to(audioExchange())
                .with(dlqRoutingKey);
    }

    /**
     * Configure binding between exchange and main audio processing queue.
     *
     * This binding routes messages with the audio.process routing key to the
     * audio processing queue. The AudioProcessingProducer sends messages using
     * this routing key, and the AudioProcessingConsumer listens to this queue.
     *
     * @return Binding for audio processing queue
     */
    @Bean
    public Binding audioProcessingBinding() {
        log.debug("Binding queue {} to exchange {} with routing key {}",
                audioProcessingQueue, audioExchange, audioRoutingKey);

        return BindingBuilder
                .bind(audioProcessingQueue())
                .to(audioExchange())
                .with(audioRoutingKey);
    }

    /**
     * Configure custom admin for RabbitMQ queue/exchange declaration.
     *
     * The RabbitAdmin declares exchanges, queues, and bindings on startup.
     * This ensures the RabbitMQ infrastructure is ready before the application
     * starts processing messages.
     *
     * @param connectionFactory the RabbitMQ connection factory
     * @return RabbitAdmin for automatic declaration
     */
    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        log.info("Configuring AmqpAdmin for automatic queue/exchange declaration");
        return new RabbitAdmin(connectionFactory);
    }
}
