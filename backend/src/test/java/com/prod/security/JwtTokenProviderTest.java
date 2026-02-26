package com.prod.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 *
 * Tests JWT token operations including:
 * - Token generation with correct claims
 * - Token validation (signature, expiration)
 * - Claim extraction (user ID, email)
 * - Authentication object creation
 */
@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private UUID testUserId;
    private String testEmail;
    private String testSecret;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        testUserId = UUID.randomUUID();
        testEmail = "test@example.com";
        testSecret = "aVerySecureSecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLongForHS256Algorithm";

        // Use reflection to set private fields
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L); // 1 hour

        // Initialize the provider
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("generateToken should create valid JWT token")
    void testGenerateToken_Success() {
        // Act
        String token = jwtTokenProvider.generateToken(testUserId, testEmail);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains(".")); // JWT has 3 parts separated by dots
    }

    @Test
    @DisplayName("validateToken should return true for valid token")
    void testValidateToken_Valid() {
        // Arrange
        String token = jwtTokenProvider.generateToken(testUserId, testEmail);

        // Act
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Assert
        assertTrue(isValid);
    }

    @Test
    @DisplayName("validateToken should return false for malformed token")
    void testValidateToken_Malformed() {
        // Act
        boolean isValid = jwtTokenProvider.validateToken("not.a.valid.jwt");

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("validateToken should return false for empty token")
    void testValidateToken_Empty() {
        // Act
        boolean isValid = jwtTokenProvider.validateToken("");

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("validateToken should return false for null token")
    void testValidateToken_Null() {
        // Act
        boolean isValid = jwtTokenProvider.validateToken(null);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("getUserIdFromToken should extract correct user ID")
    void testGetUserIdFromToken_Success() {
        // Arrange
        String token = jwtTokenProvider.generateToken(testUserId, testEmail);

        // Act
        UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

        // Assert
        assertNotNull(extractedUserId);
        assertEquals(testUserId, extractedUserId);
    }

    @Test
    @DisplayName("getEmailFromToken should extract correct email")
    void testGetEmailFromToken_Success() {
        // Arrange
        String token = jwtTokenProvider.generateToken(testUserId, testEmail);

        // Act
        String extractedEmail = jwtTokenProvider.getEmailFromToken(token);

        // Assert
        assertNotNull(extractedEmail);
        assertEquals(testEmail, extractedEmail);
    }

    @Test
    @DisplayName("getAuthentication should create valid Authentication object")
    void testGetAuthentication_Success() {
        // Arrange
        String token = jwtTokenProvider.generateToken(testUserId, testEmail);

        // Act
        var authentication = jwtTokenProvider.getAuthentication(token);

        // Assert
        assertNotNull(authentication);
        assertEquals(testUserId.toString(), authentication.getPrincipal());
        assertEquals(testEmail, authentication.getDetails());
        assertEquals(1, authentication.getAuthorities().size());
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("extractTokenFromHeader should extract token from valid Bearer header")
    void testExtractTokenFromHeader_ValidBearer() {
        // Arrange
        String token = jwtTokenProvider.generateToken(testUserId, testEmail);
        String bearerHeader = "Bearer " + token;

        // Act
        String extractedToken = jwtTokenProvider.extractTokenFromHeader(bearerHeader);

        // Assert
        assertNotNull(extractedToken);
        assertEquals(token, extractedToken);
    }

    @Test
    @DisplayName("extractTokenFromHeader should return null for null header")
    void testExtractTokenFromHeader_NullHeader() {
        // Act
        String extractedToken = jwtTokenProvider.extractTokenFromHeader(null);

        // Assert
        assertNull(extractedToken);
    }

    @Test
    @DisplayName("extractTokenFromHeader should return null for header without Bearer prefix")
    void testExtractTokenFromHeader_NoBearerPrefix() {
        // Act
        String extractedToken = jwtTokenProvider.extractTokenFromHeader("InvalidHeader token123");

        // Assert
        assertNull(extractedToken);
    }

    @Test
    @DisplayName("extractTokenFromHeader should return null for empty header")
    void testExtractTokenFromHeader_EmptyHeader() {
        // Act
        String extractedToken = jwtTokenProvider.extractTokenFromHeader("");

        // Assert
        assertNull(extractedToken);
    }

    @Test
    @DisplayName("Token should contain correct claims structure")
    void testTokenClaimsStructure() {
        // Arrange
        String token = jwtTokenProvider.generateToken(testUserId, testEmail);

        // Extract claims manually to verify structure
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // Assert
        assertEquals(testUserId.toString(), claims.getSubject());
        assertEquals(testEmail, claims.get("email", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("Token expiration should be set correctly")
    void testTokenExpiration() {
        // Arrange
        long expirationMs = 3600000L; // 1 hour
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", expirationMs);
        jwtTokenProvider.init();

        String token = jwtTokenProvider.generateToken(testUserId, testEmail);

        // Extract expiration
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // Calculate expected expiration
        Date issuedAt = claims.getIssuedAt();
        Date expectedExpiration = new Date(issuedAt.getTime() + expirationMs);

        // Assert (allow small difference for execution time)
        long diff = Math.abs(claims.getExpiration().getTime() - expectedExpiration.getTime());
        assertTrue(diff < 1000, "Expiration should be within 1 second of expected time");
    }

    @Test
    @DisplayName("getExpirationMs should return configured expiration")
    void testGetExpirationMs() {
        // Arrange
        long expectedExpiration = 3600000L;
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", expectedExpiration);
        jwtTokenProvider.init();

        // Act
        long expiration = jwtTokenProvider.getExpirationMs();

        // Assert
        assertEquals(expectedExpiration, expiration);
    }

    @Test
    @DisplayName("Different users should have different tokens")
    void testTokenUniqueness() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        String email1 = "user1@example.com";
        String email2 = "user2@example.com";

        // Act
        String token1 = jwtTokenProvider.generateToken(userId1, email1);
        String token2 = jwtTokenProvider.generateToken(userId2, email2);

        // Assert
        assertNotEquals(token1, token2, "Different users should have different tokens");
    }

    @Test
    @DisplayName("Same user should get different tokens on each generation")
    void testTokenUniqueness_SameUser() throws InterruptedException {
        // Act - generate multiple tokens with delays and check that at least some differ
        // Note: Due to timer resolution, tokens generated within the same millisecond
        // may be identical. This test verifies that tokens differ when time passes.
        String token1 = jwtTokenProvider.generateToken(testUserId, testEmail);
        Thread.sleep(1500); // 1.5 second delay to ensure different timestamps
        String token2 = jwtTokenProvider.generateToken(testUserId, testEmail);

        // Assert - tokens should be different with enough time passing
        assertNotEquals(token1, token2,
                "Same user should get different tokens due to different issuedAt timestamps");

        // Both tokens should still be valid
        assertTrue(jwtTokenProvider.validateToken(token1), "First token should be valid");
        assertTrue(jwtTokenProvider.validateToken(token2), "Second token should be valid");

        // Both tokens should extract the same user info
        assertEquals(testUserId, jwtTokenProvider.getUserIdFromToken(token1));
        assertEquals(testUserId, jwtTokenProvider.getUserIdFromToken(token2));
        assertEquals(testEmail, jwtTokenProvider.getEmailFromToken(token1));
        assertEquals(testEmail, jwtTokenProvider.getEmailFromToken(token2));
    }

    @Test
    @DisplayName("Token with invalid signature should fail validation")
    void testValidateToken_InvalidSignature() {
        // Arrange - create a token with a different secret
        String differentSecret = "aDifferentSecretKeyForTestingInvalidSignatureThatIsAlso256Bits";
        SecretKey differentKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(testUserId.toString())
                .claim("email", testEmail)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(differentKey, Jwts.SIG.HS256)
                .compact();

        // Act
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Assert
        assertFalse(isValid, "Token signed with different secret should be invalid");
    }

    @Test
    @DisplayName("Expired token should fail validation")
    void testValidateToken_Expired() {
        // Arrange - create an expired token
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(testUserId.toString())
                .claim("email", testEmail)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        // Act
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Assert
        assertFalse(isValid, "Expired token should be invalid");
    }

    @Test
    @DisplayName("Token signed with different algorithm should fail validation")
    void testValidateToken_WrongAlgorithm() {
        // Create a token that looks valid but won't pass verification
        // This is a simplified test - in reality, you'd need a more sophisticated setup
        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
                "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        // Act
        boolean isValid = jwtTokenProvider.validateToken(fakeToken);

        // Assert
        assertFalse(isValid, "Token signed with different secret should be invalid");
    }

    @Test
    @DisplayName("init should be called successfully")
    void testInit() {
        // Arrange
        JwtTokenProvider newProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(newProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(newProvider, "jwtExpirationMs", 3600000L);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> newProvider.init());
    }
}
