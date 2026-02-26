package com.prod.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing One-Time Password (OTP) operations using Redis.
 *
 * This service handles OTP generation, storage, and verification for the
 * whitelist-based authentication system. OTPs are stored in Redis with
 * a configurable TTL and can be used for multi-factor authentication.
 *
 * Security Features:
 * - Uses SecureRandom for cryptographically strong OTP generation
 * - OTPs expire automatically via Redis TTL
 * - Rate limiting via retry counter stored in Redis
 * - Thread-safe operations (Redis is single-threaded per connection)
 *
 * Redis Key Structure:
 * - OTP storage: "otp:{email}" → OTP code
 * - Retry counter: "otp:retry:{email}" → attempt count
 *
 * @see org.springframework.data.redis.core.RedisTemplate
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OTPService {

    private final RedisTemplate<String, String> redisStringTemplate;

    @Value("${app.otp.length:6}")
    private int otpLength;

    @Value("${app.otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${app.otp.max-retries:3}")
    private int maxRetries;

    @Value("${app.otp.retry-window-hours:1}")
    private int retryWindowHours;

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String OTP_RETRY_KEY_PREFIX = "otp:retry:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate and store a 6-digit OTP for the given email.
     *
     * This method performs the following steps:
     * 1. Check if email has exceeded retry limit
     * 2. Generate a cryptographically secure random OTP
     * 3. Store OTP in Redis with TTL
     * 4. Increment retry counter
     *
     * The OTP is stored as a string value with the key "otp:{email}"
     * and expires after the configured TTL (default 5 minutes).
     *
     * @param email the user's email address
     * @return the generated OTP code
     * @throws IllegalStateException if retry limit has been exceeded
     */
    public String generateAndStoreOTP(String email) {
        String retryKey = OTP_RETRY_KEY_PREFIX + email;

        // Check retry limit
        Long retryCount = redisStringTemplate.opsForValue().get(retryKey) != null
                ? Long.parseLong(redisStringTemplate.opsForValue().get(retryKey))
                : 0L;

        if (retryCount >= maxRetries) {
            log.warn("OTP retry limit exceeded for email: {} (attempts: {})", email, retryCount);
            throw new IllegalStateException(
                    "OTP retry limit exceeded. Please try again later."
            );
        }

        // Generate OTP
        String otp = generateOTP();

        // Store OTP with TTL
        String otpKey = OTP_KEY_PREFIX + email;
        redisStringTemplate.opsForValue().set(
                otpKey,
                otp,
                otpExpirationMinutes,
                TimeUnit.MINUTES
        );

        // Increment retry counter with separate TTL for retry window
        redisStringTemplate.opsForValue().increment(retryKey);
        if (retryCount == 0) {
            // Set TTL on first retry
            redisStringTemplate.expire(retryKey, retryWindowHours, TimeUnit.HOURS);
        }

        log.info("Generated OTP for email: {} (valid for {} minutes)", email, otpExpirationMinutes);
        log.debug("OTP stored with key: {}, retry count: {}/{}", otpKey, retryCount + 1, maxRetries);

        return otp;
    }

    /**
     * Verify OTP for the given email.
     *
     * This method checks if the provided OTP matches the stored OTP in Redis.
     * If verification is successful, the OTP is deleted to prevent reuse.
     *
     * @param email the user's email address
     * @param otp the OTP code to verify
     * @return true if OTP is valid, false otherwise
     */
    public boolean verifyOTP(String email, String otp) {
        String otpKey = OTP_KEY_PREFIX + email;
        String storedOtp = redisStringTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            log.warn("OTP not found or expired for email: {}", email);
            return false;
        }

        boolean isValid = storedOtp.equals(otp);

        if (isValid) {
            // Delete OTP after successful verification to prevent reuse
            redisStringTemplate.delete(otpKey);
            // Also clear retry counter on successful verification
            redisStringTemplate.delete(OTP_RETRY_KEY_PREFIX + email);
            log.info("OTP verified successfully for email: {}", email);
        } else {
            log.warn("Invalid OTP attempt for email: {}", email);
        }

        return isValid;
    }

    /**
     * Check if an OTP exists for the given email.
     *
     * This method can be used to check if an OTP has been issued for an email
     * without revealing the actual OTP value.
     *
     * @param email the user's email address
     * @return true if OTP exists and has not expired, false otherwise
     */
    public boolean hasOTP(String email) {
        String otpKey = OTP_KEY_PREFIX + email;
        Boolean hasKey = redisStringTemplate.hasKey(otpKey);
        return Boolean.TRUE.equals(hasKey);
    }

    /**
     * Get remaining time to live for an OTP in seconds.
     *
     * Useful for displaying countdown timers in the frontend.
     *
     * @param email the user's email address
     * @return TTL in seconds, or null if OTP doesn't exist
     */
    public Long getOTPTTL(String email) {
        String otpKey = OTP_KEY_PREFIX + email;
        return redisStringTemplate.getExpire(otpKey, TimeUnit.SECONDS);
    }

    /**
     * Clear all OTP data for a given email.
     *
     * This method removes both the OTP and retry counter from Redis.
     * Useful for administrative purposes or when a user resets their
     * authentication flow.
     *
     * @param email the user's email address
     */
    public void clearOTP(String email) {
        String otpKey = OTP_KEY_PREFIX + email;
        String retryKey = OTP_RETRY_KEY_PREFIX + email;

        redisStringTemplate.delete(otpKey);
        redisStringTemplate.delete(retryKey);

        log.info("Cleared OTP data for email: {}", email);
    }

    /**
     * Get current retry count for an email.
     *
     * @param email the user's email address
     * @return number of OTP requests made within the retry window
     */
    public long getRetryCount(String email) {
        String retryKey = OTP_RETRY_KEY_PREFIX + email;
        String count = redisStringTemplate.opsForValue().get(retryKey);
        return count != null ? Long.parseLong(count) : 0;
    }

    /**
     * Generate a cryptographically secure random OTP code.
     *
     * Uses SecureRandom to generate a random number with the configured
     * number of digits (default 6). The OTP is left-padded with zeros
     * to ensure consistent length (e.g., "001234").
     *
     * @return the generated OTP code as a string
     */
    private String generateOTP() {
        int max = (int) Math.pow(10, otpLength);
        int otp = SECURE_RANDOM.nextInt(max);
        return String.format("%0" + otpLength + "d", otp);
    }

    /**
     * Get the configured OTP expiration time in minutes.
     *
     * @return OTP expiration time in minutes
     */
    public int getOTPExpirationMinutes() {
        return otpExpirationMinutes;
    }

    /**
     * Get the maximum retry limit.
     *
     * @return maximum number of OTP attempts allowed per window
     */
    public int getMaxRetries() {
        return maxRetries;
    }
}
