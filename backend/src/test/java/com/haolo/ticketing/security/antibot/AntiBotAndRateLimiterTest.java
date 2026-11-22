package com.haolo.ticketing.security.antibot;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.security.antibot.AntiBotModels.ChallengeResponse;
import com.haolo.ticketing.security.antibot.AntiBotModels.VerifyChallengeResponse;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AntiBotAndRateLimiterTest {

    private JdbcTemplate jdbc;
    private HmacProofOfWorkChallengeAdapter challengeAdapter;
    private RateLimiterService rateLimiterService;
    private AntiBotService antiBotService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        challengeAdapter = new HmacProofOfWorkChallengeAdapter(jdbc);
        rateLimiterService = new RateLimiterService(jdbc);
        antiBotService = new AntiBotService(challengeAdapter, rateLimiterService, jdbc);
    }

    @Test
    void challengeAdapter_generatesChallengeWithQuestion() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        ChallengeResponse response = challengeAdapter.createChallenge("192.168.1.1", accountId, eventId);

        assertNotNull(response);
        assertNotNull(response.challengeToken());
        assertTrue(response.challengeToken().startsWith("CHAL-"));
        assertTrue(response.question().contains("+"));
        assertEquals(300L, response.expiresInSeconds());

        verify(jdbc).update(contains("INSERT INTO ticketing.anti_bot_challenges"),
            eq(response.challengeToken()), eq("192.168.1.1"), eq(accountId), eq(eventId),
            contains("?"), anyString(), any(Timestamp.class));
    }

    @Test
    void challengeAdapter_mockBypassSolutionSucceeds() {
        String token = "CHAL-TEST-123";
        when(jdbc.queryForList(contains("FROM ticketing.anti_bot_challenges WHERE challenge_token = ?"), eq(token)))
            .thenReturn(List.of(Map.of(
                "id", UUID.randomUUID(),
                "solution_hash", "somehash",
                "status", "PENDING",
                "expires_at", Timestamp.from(Instant.now().plusSeconds(300)),
                "event_id", UUID.randomUUID()
            )));

        VerifyChallengeResponse response = challengeAdapter.verifyChallenge(token, "MOCK-BYPASS", "127.0.0.1");

        assertTrue(response.verified());
        assertNotNull(response.admissionToken());
        assertTrue(response.admissionToken().startsWith("ADMIT-"));
        assertEquals(900L, response.expiresInSeconds());
    }

    @Test
    void challengeAdapter_expiredChallengeFails() {
        String token = "CHAL-EXPIRED";
        when(jdbc.queryForList(contains("FROM ticketing.anti_bot_challenges WHERE challenge_token = ?"), eq(token)))
            .thenReturn(List.of(Map.of(
                "id", UUID.randomUUID(),
                "solution_hash", "somehash",
                "status", "PENDING",
                "expires_at", Timestamp.from(Instant.now().minusSeconds(10)),
                "event_id", UUID.randomUUID()
            )));

        VerifyChallengeResponse response = challengeAdapter.verifyChallenge(token, "123", "127.0.0.1");

        assertFalse(response.verified());
        assertNull(response.admissionToken());
        assertTrue(response.message().contains("quá hạn"));
    }

    @Test
    void rateLimiter_allowsUnderThresholdAndBlocksWhenExceeded() {
        String ip = "10.0.0.99";
        String endpoint = "/api/bookings";
        rateLimiterService.clearAll();

        // Limit: 3 requests / 5 seconds
        assertDoesNotThrow(() -> rateLimiterService.assertAllowed(ip, endpoint, null, 3, 5));
        assertDoesNotThrow(() -> rateLimiterService.assertAllowed(ip, endpoint, null, 3, 5));
        assertDoesNotThrow(() -> rateLimiterService.assertAllowed(ip, endpoint, null, 3, 5));

        // 4th request must throw 429
        ApiException ex = assertThrows(ApiException.class, () ->
            rateLimiterService.assertAllowed(ip, endpoint, null, 3, 5)
        );
        assertEquals(429, ex.status);
        assertEquals("RATE_LIMIT_EXCEEDED", ex.code);
    }

    @Test
    void antiBotService_assertAntiBotVerificationThrowsWhenProtectedAndMissingToken() {
        UUID eventId = UUID.randomUUID();
        when(jdbc.queryForObject(contains("SELECT anti_bot_enabled FROM ticketing.events"), eq(Boolean.class), eq(eventId)))
            .thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
            antiBotService.assertAntiBotVerification(null, eventId)
        );
        assertEquals(403, ex.status);
        assertEquals("ANTI_BOT_CHALLENGE_REQUIRED", ex.code);
    }
}
