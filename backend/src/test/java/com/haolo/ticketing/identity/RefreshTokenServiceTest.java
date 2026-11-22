package com.haolo.ticketing.identity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.shared.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private JdbcTemplate jdbc;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new RefreshTokenService(jdbc, 7);
    }

    @Test
    void createRefreshTokenInsertsHashedToken() {
        UUID userId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(UUID.class), eq(userId), anyString(), any(UUID.class), any(java.sql.Timestamp.class), any(), any()))
            .thenReturn(1);

        String rawToken = service.createRefreshToken(userId, "Mozilla/5.0", "127.0.0.1");

        assertNotNull(rawToken);
        assertEquals(64, rawToken.length()); // 32 bytes hex = 64 chars
        verify(jdbc, times(1)).update(
            contains("INSERT INTO ticketing.refresh_tokens"),
            any(UUID.class), eq(userId), anyString(), any(UUID.class), any(java.sql.Timestamp.class), eq("Mozilla/5.0"), eq("127.0.0.1")
        );
    }

    @Test
    void hashTokenIsDeterministicSha256() {
        String rawToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String hash1 = service.hashToken(rawToken);
        String hash2 = service.hashToken(rawToken);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    void rotateRefreshTokenSuccessWhenTokenValid() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String rawToken = "a1b2c3d4e5f60123456789abcdef0123456789abcdef0123456789abcdef0123";

        RefreshTokenService.TokenRecord existing = new RefreshTokenService.TokenRecord(
            tokenId, userId, familyId, false, Instant.now().plus(7, ChronoUnit.DAYS), null
        );

        when(jdbc.query(contains("SELECT id, user_id"), any(RowMapper.class), anyString()))
            .thenReturn(List.of(existing));

        var result = service.rotateRefreshToken(rawToken);

        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertNotNull(result.newRawToken());
        assertEquals(64, result.newRawToken().length());

        // Verify old token is marked revoked and replaced
        verify(jdbc, times(1)).update(
            contains("UPDATE ticketing.refresh_tokens SET is_revoked = true, replaced_by = ? WHERE id = ?"),
            any(UUID.class), eq(tokenId)
        );

        // Verify new token is inserted with same familyId
        verify(jdbc, times(1)).update(
            contains("INSERT INTO ticketing.refresh_tokens"),
            any(UUID.class), eq(userId), anyString(), eq(familyId), any(java.sql.Timestamp.class), any(), any()
        );
    }

    @Test
    void reuseDetectionRevokesEntireFamilyWhenTokenAlreadyReplaced() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID olderReplacedBy = UUID.randomUUID();
        String stolenRawToken = "stolen_token_hex_1234567890abcdef1234567890abcdef1234567890abcdef";

        // Token has already been replaced by another token!
        RefreshTokenService.TokenRecord reusedToken = new RefreshTokenService.TokenRecord(
            tokenId, userId, familyId, true, Instant.now().plus(5, ChronoUnit.DAYS), olderReplacedBy
        );

        when(jdbc.query(contains("SELECT id, user_id"), any(RowMapper.class), anyString()))
            .thenReturn(List.of(reusedToken));

        ApiException ex = assertThrows(ApiException.class, () -> service.rotateRefreshToken(stolenRawToken));

        assertEquals(401, ex.status);
        assertEquals("TOKEN_REUSE_DETECTED", ex.code);

        // Crucial security verification: entire token family is revoked in PostgreSQL!
        verify(jdbc, times(1)).update(
            contains("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE family_id = ?"),
            eq(familyId)
        );
    }

    @Test
    void expiredTokenThrowsUnauthorized() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String rawToken = "expired_token_hex_1234567890abcdef1234567890abcdef1234567890abcdef";

        // Token expired 1 hour ago
        RefreshTokenService.TokenRecord expiredToken = new RefreshTokenService.TokenRecord(
            tokenId, userId, familyId, false, Instant.now().minus(1, ChronoUnit.HOURS), null
        );

        when(jdbc.query(contains("SELECT id, user_id"), any(RowMapper.class), anyString()))
            .thenReturn(List.of(expiredToken));

        ApiException ex = assertThrows(ApiException.class, () -> service.rotateRefreshToken(rawToken));

        assertEquals(401, ex.status);
        assertEquals("REFRESH_TOKEN_EXPIRED", ex.code);
    }

    @Test
    void missingTokenThrowsValidationError() {
        assertThrows(ApiException.class, () -> service.rotateRefreshToken(null));
        assertThrows(ApiException.class, () -> service.rotateRefreshToken(""));
    }

    @Test
    void getActiveSessionsReturnsMappedList() {
        UUID userId = UUID.randomUUID();
        UUID currentFamilyId = UUID.randomUUID();

        when(jdbc.query(contains("SELECT DISTINCT ON (family_id)"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of(
                new RefreshTokenService.ActiveSessionDto(currentFamilyId, "Chrome", "192.168.1.1", Instant.now(), Instant.now().plus(7, ChronoUnit.DAYS), true)
            ));

        var sessions = service.getActiveSessions(userId, currentFamilyId);
        assertNotNull(sessions);
        assertEquals(1, sessions.size());
        assertTrue(sessions.getFirst().current());
        assertEquals("Chrome", sessions.getFirst().userAgent());
    }

    @Test
    void revokeFamilyUpdatesRevokedStatus() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        when(jdbc.update(contains("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ? AND family_id = ?"), eq(userId), eq(familyId)))
            .thenReturn(1);

        service.revokeFamily(userId, familyId);
        verify(jdbc, times(1)).update(contains("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ? AND family_id = ?"), eq(userId), eq(familyId));
    }

    @Test
    void revokeOtherFamiliesUpdatesOtherFamilies() {
        UUID userId = UUID.randomUUID();
        UUID currentFamilyId = UUID.randomUUID();

        when(jdbc.update(contains("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ? AND family_id <> ?"), eq(userId), eq(currentFamilyId)))
            .thenReturn(2);

        service.revokeOtherFamilies(userId, currentFamilyId);
        verify(jdbc, times(1)).update(contains("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ? AND family_id <> ?"), eq(userId), eq(currentFamilyId));
    }
}
