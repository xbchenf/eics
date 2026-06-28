package com.eics.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-for-unit-tests");
        ReflectionTestUtils.setField(jwtUtil, "expireHours", 1);
    }

    @Test
    void testCreateAndVerifyToken() {
        String token = jwtUtil.createToken(1L, "admin", "ADMIN");
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT should have 3 parts");

        Map<String, Object> claims = jwtUtil.verifyToken(token);
        assertNotNull(claims);
        assertEquals(1L, claims.get("agentId"));
        assertEquals("admin", claims.get("username"));
        assertEquals("ADMIN", claims.get("role"));
    }

    @Test
    void testVerifyInvalidToken() {
        assertNull(jwtUtil.verifyToken("invalid.token.here"));
        assertNull(jwtUtil.verifyToken(""));
        assertNull(jwtUtil.verifyToken(null));
    }

    @Test
    void testVerifyWrongSecret() {
        String token = jwtUtil.createToken(1L, "admin", "ADMIN");
        // Create another instance with different secret
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "different-secret");
        ReflectionTestUtils.setField(other, "expireHours", 1);
        assertNull(other.verifyToken(token));
    }

    @Test
    void testExpiredToken() {
        // Create token that expires immediately
        JwtUtil shortLived = new JwtUtil();
        ReflectionTestUtils.setField(shortLived, "secret", "test-secret");
        ReflectionTestUtils.setField(shortLived, "expireHours", -1); // expired 1 hour ago
        String token = shortLived.createToken(1L, "admin", "ADMIN");

        JwtUtil verifier = new JwtUtil();
        ReflectionTestUtils.setField(verifier, "secret", "test-secret");
        ReflectionTestUtils.setField(verifier, "expireHours", 1);
        assertNull(verifier.verifyToken(token), "Expired token should return null");
    }

    @Test
    void testTokenForDifferentAgent() {
        String token = jwtUtil.createToken(42L, "agent1", "AGENT");
        Map<String, Object> claims = jwtUtil.verifyToken(token);
        assertNotNull(claims);
        assertEquals(42L, claims.get("agentId"));
        assertEquals("AGENT", claims.get("role"));
    }
}
