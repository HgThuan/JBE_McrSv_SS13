package com.example.identity.util;

import com.example.identity.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expiration = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(secret, expiration);
    }

    @Test
    @DisplayName("generateToken tạo JWT hợp lệ gồm đúng 3 phần (Header, Payload, Signature)")
    void testGenerateTokenStructure() {
        User user = new User("uuid-123", "alice", "hashedPassword", "ROLE_ADMIN");

        String token = jwtUtil.generateToken(user);

        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT phải gồm đúng 3 phần phân cách bởi dấu chấm (Header.Payload.Signature)");

        // Header Base64 URL decode
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
        assertTrue(headerJson.contains("HS256"), "Header phải chỉ rõ thuật toán HS256");

        // Payload Base64 URL decode
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertTrue(payloadJson.contains("\"sub\":\"alice\""), "Payload phải chứa standard claim sub = alice");
        assertTrue(payloadJson.contains("\"role\":\"ROLE_ADMIN\""), "Payload phải chứa custom claim role = ROLE_ADMIN");
        assertTrue(payloadJson.contains("\"iat\":"), "Payload phải chứa claim iat");
        assertTrue(payloadJson.contains("\"exp\":"), "Payload phải chứa claim exp");
    }

    @Test
    @DisplayName("JwtUtil trích xuất chính xác username, role, expiration và validate thành công")
    void testExtractClaimsAndValidation() {
        User user = new User("uuid-456", "bob", "hashedPassword", "ROLE_USER");
        String token = jwtUtil.generateToken(user);

        // Trích xuất claims
        assertEquals("bob", jwtUtil.extractUsername(token));
        assertEquals("ROLE_USER", jwtUtil.extractRole(token));
        assertFalse(jwtUtil.isTokenExpired(token));
        assertTrue(jwtUtil.validateToken(token, user));

        // User khác validate phải false
        User anotherUser = new User("uuid-789", "charlie", "pass", "ROLE_USER");
        assertFalse(jwtUtil.validateToken(token, anotherUser));

        Claims claims = jwtUtil.extractAllClaims(token);
        assertEquals("bob", claims.getSubject());
        assertEquals("ROLE_USER", claims.get("role", String.class));
    }
}
