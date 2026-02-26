package com.prod.service;

import com.prod.entity.User;
import com.prod.exception.WhitelistException;
import com.prod.repository.UserRepository;
import com.prod.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 *
 * Tests the business logic for authentication including:
 * - OTP request with whitelist validation
 * - OTP verification and JWT generation
 * - Whitelist checking
 * - Token refresh
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OTPService otpService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UUID testUserId;
    private String testEmail;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEmail = "test@example.com";
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail(testEmail);
        testUser.setIsWhitelisted(true);
    }

    @Test
    @DisplayName("requestOTP should generate OTP for whitelisted user")
    void testRequestOTP_Success() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(otpService.generateAndStoreOTP(anyString())).thenReturn("123456");
        when(otpService.getOTPExpirationMinutes()).thenReturn(5);

        // Act
        String otp = authService.requestOTP(testEmail);

        // Assert
        assertNotNull(otp);
        assertEquals("123456", otp);
        verify(userRepository).findByEmail(testEmail.toLowerCase());
        verify(otpService).generateAndStoreOTP(testEmail.toLowerCase());
    }

    @Test
    @DisplayName("requestOTP should throw WhitelistException for non-whitelisted user")
    void testRequestOTP_NonWhitelistedUser() {
        // Arrange
        testUser.setIsWhitelisted(false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(WhitelistException.class, () -> authService.requestOTP(testEmail));
        verify(otpService, never()).generateAndStoreOTP(anyString());
    }

    @Test
    @DisplayName("requestOTP should throw WhitelistException for non-existent user")
    void testRequestOTP_UserNotFound() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(WhitelistException.class, () -> authService.requestOTP(testEmail));
        verify(otpService, never()).generateAndStoreOTP(anyString());
    }

    @Test
    @DisplayName("requestOTP should throw IllegalArgumentException for null email")
    void testRequestOTP_NullEmail() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authService.requestOTP(null));
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("requestOTP should throw IllegalArgumentException for empty email")
    void testRequestOTP_EmptyEmail() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authService.requestOTP("   "));
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("verifyOTPAndGenerateToken should generate JWT for valid OTP")
    void testVerifyOTPAndGenerateToken_Success() {
        // Arrange
        String otp = "123456";
        String jwtToken = "jwt.token.here";

        when(otpService.verifyOTP(anyString(), anyString())).thenReturn(true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateToken(any(UUID.class), anyString())).thenReturn(jwtToken);

        // Act
        String result = authService.verifyOTPAndGenerateToken(testEmail, otp);

        // Assert
        assertNotNull(result);
        assertEquals(jwtToken, result);
        verify(otpService).verifyOTP(testEmail.toLowerCase(), otp);
        verify(userRepository).findByEmail(testEmail.toLowerCase());
        verify(jwtTokenProvider).generateToken(testUserId, testEmail);
    }

    @Test
    @DisplayName("verifyOTPAndGenerateToken should throw SecurityException for invalid OTP")
    void testVerifyOTPAndGenerateToken_InvalidOTP() {
        // Arrange
        when(otpService.verifyOTP(anyString(), anyString())).thenReturn(false);

        // Act & Assert
        assertThrows(SecurityException.class, () -> authService.verifyOTPAndGenerateToken(testEmail, "000000"));
        verify(userRepository, never()).findByEmail(anyString());
        verify(jwtTokenProvider, never()).generateToken(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("verifyOTPAndGenerateToken should throw WhitelistException if user loses whitelist status")
    void testVerifyOTPAndGenerateToken_UserLostWhitelist() {
        // Arrange
        testUser.setIsWhitelisted(false);
        when(otpService.verifyOTP(anyString(), anyString())).thenReturn(true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(WhitelistException.class, () -> authService.verifyOTPAndGenerateToken(testEmail, "123456"));
        verify(jwtTokenProvider, never()).generateToken(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("isWhitelisted should return true for whitelisted user")
    void testIsWhitelisted_True() {
        // Arrange
        when(userRepository.findByEmailAndIsWhitelisted(anyString(), anyBoolean()))
                .thenReturn(Optional.of(testUser));

        // Act
        boolean result = authService.isWhitelisted(testEmail);

        // Assert
        assertTrue(result);
        verify(userRepository).findByEmailAndIsWhitelisted(testEmail.toLowerCase(), true);
    }

    @Test
    @DisplayName("isWhitelisted should return false for non-whitelisted user")
    void testIsWhitelisted_False() {
        // Arrange
        when(userRepository.findByEmailAndIsWhitelisted(anyString(), anyBoolean()))
                .thenReturn(Optional.empty());

        // Act
        boolean result = authService.isWhitelisted("blocked@example.com");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("isWhitelisted should return false for null email")
    void testIsWhitelisted_NullEmail() {
        // Act
        boolean result = authService.isWhitelisted(null);

        // Assert
        assertFalse(result);
        verify(userRepository, never()).findByEmailAndIsWhitelisted(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("getUserIdByEmail should return user ID for existing user")
    void testGetUserIdByEmail_Success() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        // Act
        UUID result = authService.getUserIdByEmail(testEmail);

        // Assert
        assertNotNull(result);
        assertEquals(testUserId, result);
        verify(userRepository).findByEmail(testEmail.toLowerCase());
    }

    @Test
    @DisplayName("getUserIdByEmail should return null for non-existent user")
    void testGetUserIdByEmail_UserNotFound() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act
        UUID result = authService.getUserIdByEmail("nonexistent@example.com");

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("refreshToken should generate new token for valid user")
    void testRefreshToken_Success() {
        // Arrange
        String newToken = "new.jwt.token";
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateToken(any(UUID.class), anyString())).thenReturn(newToken);

        // Act
        String result = authService.refreshToken(testUserId, testEmail);

        // Assert
        assertNotNull(result);
        assertEquals(newToken, result);
        verify(userRepository).findById(testUserId);
        verify(jwtTokenProvider).generateToken(testUserId, testEmail);
    }

    @Test
    @DisplayName("refreshToken should throw WhitelistException if user not found")
    void testRefreshToken_UserNotFound() {
        // Arrange
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(WhitelistException.class, () -> authService.refreshToken(testUserId, testEmail));
        verify(jwtTokenProvider, never()).generateToken(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("refreshToken should throw WhitelistException if user lost whitelist status")
    void testRefreshToken_UserLostWhitelist() {
        // Arrange
        testUser.setIsWhitelisted(false);
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(WhitelistException.class, () -> authService.refreshToken(testUserId, testEmail));
        verify(jwtTokenProvider, never()).generateToken(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("refreshToken should throw SecurityException for email mismatch")
    void testRefreshToken_EmailMismatch() {
        // Arrange
        User differentUser = new User();
        differentUser.setId(testUserId);
        differentUser.setEmail("different@example.com");
        differentUser.setIsWhitelisted(true);

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(differentUser));

        // Act & Assert
        assertThrows(SecurityException.class, () -> authService.refreshToken(testUserId, testEmail));
        verify(jwtTokenProvider, never()).generateToken(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("Email should be normalized (trimmed and lowercased)")
    void testEmailNormalization() {
        // Arrange
        String emailWithSpaces = "  TEST@EXAMPLE.COM  ";
        String normalizedEmail = "test@example.com";

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(otpService.generateAndStoreOTP(anyString())).thenReturn("123456");

        // Act
        authService.requestOTP(emailWithSpaces);

        // Assert
        verify(userRepository).findByEmail(normalizedEmail);
        verify(otpService).generateAndStoreOTP(normalizedEmail);
    }
}
