package com.haolo.ticketing.identity;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final JwtService jwtService = new JwtService(
        "test-secret-key-for-unit-tests-needs-to-be-sufficiently-long-for-hmac256",
        24,
        mapper
    );

    @Test
    void tokenGenerationAndValidationSucceeds() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "test@eventflow.vn", "CUSTOMER", "Nguyen Van A");
        assertNotNull(token);
        assertTrue(token.contains("."));

        var claimsOpt = jwtService.validateAndExtract(token);
        assertTrue(claimsOpt.isPresent());
        var claims = claimsOpt.get();
        assertEquals(userId, claims.userId());
        assertEquals("test@eventflow.vn", claims.email());
        assertEquals("CUSTOMER", claims.role());
        assertEquals("Nguyen Van A", claims.displayName());
    }

    @Test
    void tamperedTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "test@eventflow.vn", "CUSTOMER", "User");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        var claimsOpt = jwtService.validateAndExtract(tampered);
        assertTrue(claimsOpt.isEmpty());
    }

    @Test
    void nullOrMalformedTokenIsRejected() {
        assertTrue(jwtService.validateAndExtract(null).isEmpty());
        assertTrue(jwtService.validateAndExtract("").isEmpty());
        assertTrue(jwtService.validateAndExtract("invalid.token").isEmpty());
        assertTrue(jwtService.validateAndExtract("invalid.token.structure.extra").isEmpty());
    }
}
