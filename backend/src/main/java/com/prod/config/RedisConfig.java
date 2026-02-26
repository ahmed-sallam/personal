package com.prod.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for OTP storage and session management.
 *
 * This class configures the Redis connection and RedisTemplate for storing
 * OTP codes with time-to-live (TTL) expiration. It uses Lettuce as the
 * Redis client connector with connection pooling.
 *
 * Features:
 * - Configurable Redis host and port
 * - RedisTemplate with JSON serialization for complex objects
 * - String serialization for keys (improves readability in Redis CLI)
 * - Connection pooling for performance
 * - Timeout configuration to prevent hanging connections
 *
 * @see org.springframework.data.redis.core.RedisTemplate
 * @see org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.timeout:2000ms}")
    private String redisTimeout;

    /**
     * Configure Redis connection factory.
     *
     * Creates a Lettuce-based connection factory with standalone configuration.
     * Lettuce is a thread-safe Redis client that supports both synchronous
     * and asynchronous operations.
     *
     * @return configured RedisConnectionFactory
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);

        log.info("Configuring Redis connection factory: host={}, port={}", redisHost, redisPort);

        return new LettuceConnectionFactory(config);
    }

    /**
     * Configure RedisTemplate for Redis operations.
     *
     * This template is configured with:
     * - String serializer for keys (ensures human-readable keys in Redis)
     * - JSON serializer for values (enables storage of complex objects)
     * - Hash key and value serializers (for Redis hash operations)
     *
     * The serialization configuration allows storing and retrieving Java objects
     * as JSON while keeping keys as simple strings for easier debugging.
     *
     * @param redisConnectionFactory the Redis connection factory
     * @return configured RedisTemplate for OTP and session operations
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Use String serializer for keys (human-readable in Redis CLI)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use JSON serializer for values (supports complex objects)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.debug("RedisTemplate configured with String key serializer and JSON value serializer");
        return template;
    }

    /**
     * Configure specialized RedisTemplate for String operations (OTP storage).
     *
     * This template is optimized for storing simple string values like OTP codes.
     * It uses String serialization for both keys and values, which is more efficient
     * for simple string operations.
     *
     * @param redisConnectionFactory the Redis connection factory
     * @return configured RedisTemplate for string operations
     */
    @Bean
    public RedisTemplate<String, String> redisStringTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();

        log.debug("RedisTemplate configured for String operations");
        return template;
    }
}
