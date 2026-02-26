package com.prod.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OTPService.
 *
 * Tests the OTP management including:
 * - OTP generation with secure random
 * - OTP storage in Redis with TTL
 * - OTP verification and deletion
 * - Retry limit enforcement
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OTPService Unit Tests")
class OTPServiceTest {

    @Mock
    private RedisTemplate<String, String> redisStringTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OTPService otpService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final int OTP_EXPIRATION_MINUTES = 5;
    private static final int MAX_RETRIES = 3;

    @BeforeEach
    void setUp() {
        // Reset reflection fields to test defaults
        try {
            var lengthField = OTPService.class.getDeclaredField("otpLength");
            lengthField.setAccessible(true);
            lengthField.set(otpService, 6);

            var expirationField = OTPService.class.getDeclaredField("otpExpirationMinutes");
            expirationField.setAccessible(true);
            expirationField.set(otpService, OTP_EXPIRATION_MINUTES);

            var maxRetriesField = OTPService.class.getDeclaredField("maxRetries");
            maxRetriesField.setAccessible(true);
            maxRetriesField.set(otpService, MAX_RETRIES);

            var retryWindowField = OTPService.class.getDeclaredField("retryWindowHours");
            retryWindowField.setAccessible(true);
            retryWindowField.set(otpService, 1);
        } catch (Exception e) {
            // If reflection fails, use @Value injection in actual context
        }
    }

    @Test
    @DisplayName("generateAndStoreOTP should generate 6-digit OTP")
    void testGenerateAndStoreOTP_Length() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisStringTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // Act
        String otp = otpService.generateAndStoreOTP(TEST_EMAIL);

        // Assert
        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}")); // Should be all digits
    }

    @Test
    @DisplayName("generateAndStoreOTP should store OTP with TTL")
    void testGenerateAndStoreOTP_Storage() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisStringTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // Act
        otpService.generateAndStoreOTP(TEST_EMAIL);

        // Assert
        verify(valueOperations).set(eq("otp:" + TEST_EMAIL), anyString(), eq(OTP_EXPIRATION_MINUTES), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("generateAndStoreOTP should increment retry counter")
    void testGenerateAndStoreOTP_RetryCounter() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisStringTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // Act
        otpService.generateAndStoreOTP(TEST_EMAIL);

        // Assert
        verify(valueOperations).increment(eq("otp:retry:" + TEST_EMAIL));
        verify(redisStringTemplate).expire(eq("otp:retry:" + TEST_EMAIL), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("generateAndStoreOTP should throw exception when retry limit exceeded")
    void testGenerateAndStoreOTP_RetryLimitExceeded() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("otp:retry:" + TEST_EMAIL))).thenReturn(String.valueOf(MAX_RETRIES));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> otpService.generateAndStoreOTP(TEST_EMAIL));
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("verifyOTP should return true for valid OTP")
    void testVerifyOTP_Valid() {
        // Arrange
        String otp = "123456";
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("otp:" + TEST_EMAIL))).thenReturn(otp);

        // Act
        boolean result = otpService.verifyOTP(TEST_EMAIL, otp);

        // Assert
        assertTrue(result);
        verify(redisStringTemplate).delete("otp:" + TEST_EMAIL);
        verify(redisStringTemplate).delete("otp:retry:" + TEST_EMAIL);
    }

    @Test
    @DisplayName("verifyOTP should return false for invalid OTP")
    void testVerifyOTP_Invalid() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("otp:" + TEST_EMAIL))).thenReturn("123456");

        // Act
        boolean result = otpService.verifyOTP(TEST_EMAIL, "000000");

        // Assert
        assertFalse(result);
        verify(redisStringTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("verifyOTP should return false when OTP not found")
    void testVerifyOTP_NotFound() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("otp:" + TEST_EMAIL))).thenReturn(null);

        // Act
        boolean result = otpService.verifyOTP(TEST_EMAIL, "123456");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("verifyOTP should delete OTP after successful verification")
    void testVerifyOTP_DeletesOTP() {
        // Arrange
        String otp = "123456";
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("otp:" + TEST_EMAIL))).thenReturn(otp);

        // Act
        otpService.verifyOTP(TEST_EMAIL, otp);

        // Assert
        verify(redisStringTemplate).delete("otp:" + TEST_EMAIL);
        verify(redisStringTemplate).delete("otp:retry:" + TEST_EMAIL);
    }

    @Test
    @DisplayName("hasOTP should return true when OTP exists")
    void testHasOTP_Exists() {
        // Arrange
        when(redisStringTemplate.hasKey(eq("otp:" + TEST_EMAIL))).thenReturn(true);

        // Act
        boolean result = otpService.hasOTP(TEST_EMAIL);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("hasOTP should return false when OTP doesn't exist")
    void testHasOTP_NotExists() {
        // Arrange
        when(redisStringTemplate.hasKey(eq("otp:" + TEST_EMAIL))).thenReturn(false);

        // Act
        boolean result = otpService.hasOTP(TEST_EMAIL);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("getOTPTTL should return TTL for existing OTP")
    void testGetOTPTTL_Exists() {
        // Arrange
        long expectedTTL = 300L; // 5 minutes in seconds
        when(redisStringTemplate.getExpire(eq("otp:" + TEST_EMAIL), eq(TimeUnit.SECONDS))).thenReturn(expectedTTL);

        // Act
        Long ttl = otpService.getOTPTTL(TEST_EMAIL);

        // Assert
        assertNotNull(ttl);
        assertEquals(expectedTTL, ttl);
    }

    @Test
    @DisplayName("getOTPTTL should return null for non-existent OTP")
    void testGetOTPTTL_NotExists() {
        // Arrange
        when(redisStringTemplate.getExpire(eq("otp:" + TEST_EMAIL), eq(TimeUnit.SECONDS))).thenReturn(null);

        // Act
        Long ttl = otpService.getOTPTTL(TEST_EMAIL);

        // Assert
        assertNull(ttl);
    }

    @Test
    @DisplayName("clearOTP should delete OTP and retry counter")
    void testClearOTP() {
        // Act
        otpService.clearOTP(TEST_EMAIL);

        // Assert
        verify(redisStringTemplate).delete("otp:" + TEST_EMAIL);
        verify(redisStringTemplate).delete("otp:retry:" + TEST_EMAIL);
    }

    @Test
    @DisplayName("getRetryCount should return current retry count")
    void testGetRetryCount() {
        // Arrange
        long expectedCount = 2L;
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("otp:retry:" + TEST_EMAIL))).thenReturn(String.valueOf(expectedCount));

        // Act
        long count = otpService.getRetryCount(TEST_EMAIL);

        // Assert
        assertEquals(expectedCount, count);
    }

    @Test
    @DisplayName("getRetryCount should return 0 when no retries")
    void testGetRetryCount_NoRetries() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("otp:retry:" + TEST_EMAIL))).thenReturn(null);

        // Act
        long count = otpService.getRetryCount(TEST_EMAIL);

        // Assert
        assertEquals(0L, count);
    }

    @Test
    @DisplayName("Generated OTPs should be unique (randomness test)")
    void testOTPUniqueness() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisStringTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // Act
        String otp1 = otpService.generateAndStoreOTP(TEST_EMAIL);
        String otp2 = otpService.generateAndStoreOTP("another@example.com");
        String otp3 = otpService.generateAndStoreOTP("third@example.com");

        // Assert
        // While not guaranteed to be different, with 1 million combinations they should be
        // This is a probabilistic test
        assertTrue(!otp1.equals(otp2) || !otp2.equals(otp3) || !otp1.equals(otp3),
                "At least some OTPs should be different (probabilistic)");
    }

    @Test
    @DisplayName("OTP should be left-padded with zeros")
    void testOTPPadding() {
        // Arrange
        when(redisStringTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisStringTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // Generate many OTPs and check format
        for (int i = 0; i < 100; i++) {
            String otp = otpService.generateAndStoreOTP(TEST_EMAIL + i);

            // All OTPs should be exactly 6 characters (left-padded with zeros)
            assertEquals(6, otp.length(), "OTP should always be 6 digits");
            assertTrue(otp.matches("\\d{6}"), "OTP should contain only digits");
        }
    }

    @Test
    @DisplayName("getOTPExpirationMinutes should return configured value")
    void testGetOTPExpirationMinutes() {
        // Act
        int expiration = otpService.getOTPExpirationMinutes();

        // Assert
        assertEquals(OTP_EXPIRATION_MINUTES, expiration);
    }

    @Test
    @DisplayName("getMaxRetries should return configured value")
    void testGetMaxRetries() {
        // Act
        int maxRetries = otpService.getMaxRetries();

        // Assert
        assertEquals(MAX_RETRIES, maxRetries);
    }
}
