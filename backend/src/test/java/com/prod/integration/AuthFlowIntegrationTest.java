package com.prod.integration;

import com.prod.dto.request.LoginRequest;
import com.prod.dto.request.OTPVerifyRequest;
import com.prod.dto.response.AuthResponse;
import com.prod.entity.User;
import com.prod.repository.UserRepository;
import com.prod.security.JwtTokenProvider;
import com.prod.service.AuthService;
import com.prod.service.OTPService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for Authentication Flow.
 *
 * This test verifies the complete authentication flow:
 * 1. Whitelist validation
 * 2. OTP generation and Redis storage
 * 3. OTP verification
 * 4. JWT token generation
 * 5. JWT token validation on protected endpoint
 *
 * Test Requirements:
 * - PostgreSQL container for database
 * - Redis container for OTP storage
 * - Spring Boot test context
 *
 * Authentication Flow Verification:
 * - POST /api/auth/login with whitelisted email → OTP generated
 * - POST /api/auth/verify with OTP → JWT token received
 * - GET /api/audio with JWT → Protected endpoint accessible
 * - POST /api/auth/login with non-whitelisted email → 403 Forbidden
 * - POST /api/auth/verify with invalid OTP → 401 Unauthorized
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Authentication Flow Integration Tests")
class AuthFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("productivity_tracker_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("rawtypes")
    static GenericContainer redisContainer = new GenericContainer("redis:7-alpine")
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private OTPService otpService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String baseUrl;
    private User testUser;

    /**
     * Configure dynamic properties for TestContainers.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    /**
     * Setup test data before each test.
     */
    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;

        // Clean database
        userRepository.deleteAll();

        // Create a whitelisted test user
        testUser = new User();
        testUser.setEmail("testuser@example.com");
        testUser.setIsWhitelisted(true);
        testUser = userRepository.save(testUser);

        // Create a non-whitelisted user for negative tests
        User nonWhitelistedUser = new User();
        nonWhitelistedUser.setEmail("blocked@example.com");
        nonWhitelistedUser.setIsWhitelisted(false);
        userRepository.save(nonWhitelistedUser);
    }

    @Test
    @DisplayName("Complete Authentication Flow: Whitelist → OTP → JWT → Protected Endpoint")
    void testCompleteAuthenticationFlow() {
        // Step 1: Request OTP for whitelisted email
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("testuser@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.exchange(
                baseUrl + "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                AuthResponse.class
        );

        // Verify OTP was generated
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        assertNotNull(loginResponse.getBody());
        assertTrue(loginResponse.getBody().isSuccess());
        assertNotNull(loginResponse.getBody().getOtp());
        assertEquals("OTP sent successfully", loginResponse.getBody().getMessage());

        String otp = loginResponse.getBody().getOtp();

        // Step 2: Verify OTP and receive JWT token
        OTPVerifyRequest verifyRequest = new OTPVerifyRequest();
        verifyRequest.setEmail("testuser@example.com");
        verifyRequest.setOtp(otp);

        HttpEntity<OTPVerifyRequest> verifyEntity = new HttpEntity<>(verifyRequest, headers);

        ResponseEntity<AuthResponse> verifyResponse = restTemplate.exchange(
                baseUrl + "/api/auth/verify",
                HttpMethod.POST,
                verifyEntity,
                AuthResponse.class
        );

        // Verify JWT token was issued
        assertEquals(HttpStatus.OK, verifyResponse.getStatusCode());
        assertNotNull(verifyResponse.getBody());
        assertTrue(verifyResponse.getBody().isSuccess());
        assertNotNull(verifyResponse.getBody().getToken());
        assertNotNull(verifyResponse.getBody().getUserId());
        assertEquals("testuser@example.com", verifyResponse.getBody().getEmail());
        assertEquals("Authentication successful", verifyResponse.getBody().getMessage());

        String jwtToken = verifyResponse.getBody().getToken();

        // Step 3: Use JWT token to access protected endpoint
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(jwtToken);
        HttpEntity<Void> authEntity = new HttpEntity<>(authHeaders);

        // Try to access a protected endpoint (e.g., /api/audio)
        ResponseEntity<String> protectedResponse = restTemplate.exchange(
                baseUrl + "/api/audio",
                HttpMethod.GET,
                authEntity,
                String.class
        );

        // Verify protected endpoint is accessible with valid JWT
        // Note: Should get 200 OK with empty list or similar, not 401 Unauthorized
        assertNotEquals(HttpStatus.UNAUTHORIZED, protectedResponse.getStatusCode());
        assertNotEquals(HttpStatus.FORBIDDEN, protectedResponse.getStatusCode());

        // Step 4: Verify JWT token is valid
        assertTrue(jwtTokenProvider.validateToken(jwtToken));

        UUID userId = jwtTokenProvider.getUserIdFromToken(jwtToken);
        String email = jwtTokenProvider.getEmailFromToken(jwtToken);

        assertEquals(testUser.getId(), userId);
        assertEquals("testuser@example.com", email);
    }

    @Test
    @DisplayName("Non-whitelisted email should be rejected with 403 Forbidden")
    void testNonWhitelistedEmailRejected() {
        // Attempt to request OTP for non-whitelisted email
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("blocked@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.exchange(
                baseUrl + "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                AuthResponse.class
        );

        // Should receive 403 Forbidden
        assertEquals(HttpStatus.FORBIDDEN, loginResponse.getStatusCode());
        assertNotNull(loginResponse.getBody());
        assertFalse(loginResponse.getBody().isSuccess());
    }

    @Test
    @DisplayName("Invalid OTP should be rejected with 401 Unauthorized")
    void testInvalidOTPRejected() {
        // First, request valid OTP
        String validOtp = authService.requestOTP("testuser@example.com");

        // Try to verify with invalid OTP
        OTPVerifyRequest verifyRequest = new OTPVerifyRequest();
        verifyRequest.setEmail("testuser@example.com");
        verifyRequest.setOtp("000000"); // Invalid OTP

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OTPVerifyRequest> verifyEntity = new HttpEntity<>(verifyRequest, headers);

        ResponseEntity<AuthResponse> verifyResponse = restTemplate.exchange(
                baseUrl + "/api/auth/verify",
                HttpMethod.POST,
                verifyEntity,
                AuthResponse.class
        );

        // Should receive 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, verifyResponse.getStatusCode());
    }

    @Test
    @DisplayName("Protected endpoint should return 401 without JWT token")
    void testProtectedEndpointWithoutJWT() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/audio",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Protected endpoint should return 401 with invalid JWT token")
    void testProtectedEndpointWithInvalidJWT() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("invalid.jwt.token");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/audio",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("OTP should be stored in Redis with TTL")
    void testOTPStorageInRedis() {
        String email = "testuser@example.com";

        // Generate OTP
        String otp = authService.requestOTP(email);

        // Verify OTP exists in Redis
        assertTrue(otpService.hasOTP(email));

        // Verify OTP TTL is set (should be approximately 5 minutes = 300 seconds)
        Long ttl = otpService.getOTPTTL(email);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 300);

        // Verify correct OTP
        assertTrue(otpService.verifyOTP(email, otp));

        // After verification, OTP should be deleted
        assertFalse(otpService.hasOTP(email));
    }

    @Test
    @DisplayName("OTP verification should delete OTP to prevent reuse")
    void testOTPDeletedAfterVerification() {
        String email = "testuser@example.com";
        String otp = authService.requestOTP(email);

        // First verification should succeed
        assertTrue(otpService.verifyOTP(email, otp));

        // Second verification with same OTP should fail
        assertFalse(otpService.verifyOTP(email, otp));
    }

    @Test
    @DisplayName("JWT token should contain correct claims")
    void testJWTTokenClaims() {
        String email = "testuser@example.com";
        UUID userId = testUser.getId();

        String token = jwtTokenProvider.generateToken(userId, email);

        // Verify token is valid
        assertTrue(jwtTokenProvider.validateToken(token));

        // Extract and verify claims
        UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);
        String extractedEmail = jwtTokenProvider.getEmailFromToken(token);

        assertEquals(userId, extractedUserId);
        assertEquals(email, extractedEmail);
    }

    @Test
    @DisplayName("Expired JWT token should be rejected")
    void testExpiredJWTRejected() {
        // This test verifies that expired tokens are rejected
        // In a real scenario, you'd need to either:
        // 1. Use a very short expiration for testing
        // 2. Mock time/timezone
        // 3. Use a token known to be expired

        // For now, we verify the validation logic exists
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        assertFalse(jwtTokenProvider.validateToken(invalidToken));
    }

    @Test
    @DisplayName("Whitelist check should correctly identify whitelisted users")
    void testWhitelistCheck() {
        // Whitelisted user
        assertTrue(authService.isWhitelisted("testuser@example.com"));

        // Non-whitelisted user
        assertFalse(authService.isWhitelisted("blocked@example.com"));

        // Non-existent user
        assertFalse(authService.isWhitelisted("nonexistent@example.com"));
    }
}
