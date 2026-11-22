package com.haolo.ticketing.security.antibot;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.security.antibot.AntiBotModels.ChallengeResponse;
import com.haolo.ticketing.security.antibot.AntiBotModels.VerifyChallengeResponse;
import com.haolo.ticketing.shared.ApiException;

@Service
public class AntiBotService {

    private final AntiBotChallengePort challengePort;
    private final RateLimiterService rateLimiterService;
    private final JdbcTemplate jdbc;

    public AntiBotService(AntiBotChallengePort challengePort, RateLimiterService rateLimiterService, JdbcTemplate jdbc) {
        this.challengePort = challengePort;
        this.rateLimiterService = rateLimiterService;
        this.jdbc = jdbc;
    }

    public ChallengeResponse issueChallenge(String clientIp, UUID accountId, UUID eventId) {
        // Rate limit: 20 challenge requests / minute
        rateLimiterService.assertAllowed(clientIp, "/api/security/anti-bot/challenge", accountId, 20, 60);
        return challengePort.createChallenge(clientIp, accountId, eventId);
    }

    public VerifyChallengeResponse verifyChallenge(String challengeToken, String solution, String clientIp) {
        // Rate limit: 30 verify attempts / minute
        rateLimiterService.assertAllowed(clientIp, "/api/security/anti-bot/verify", null, 30, 60);
        return challengePort.verifyChallenge(challengeToken, solution, clientIp);
    }

    public boolean isEventAntiBotProtected(UUID eventId) {
        if (eventId == null) return false;
        try {
            Boolean enabled = jdbc.queryForObject(
                "SELECT anti_bot_enabled FROM ticketing.events WHERE id = ?",
                Boolean.class,
                eventId
            );
            return Boolean.TRUE.equals(enabled);
        } catch (Exception e) {
            return false;
        }
    }

    public void setEventAntiBotProtected(UUID eventId, boolean enabled) {
        jdbc.update("UPDATE ticketing.events SET anti_bot_enabled = ? WHERE id = ?", enabled, eventId);
    }

    public void assertAntiBotVerification(String admissionToken, UUID eventId) {
        if (!isEventAntiBotProtected(eventId)) {
            return;
        }

        if (admissionToken == null || admissionToken.isBlank()) {
            throw new ApiException(403, "ANTI_BOT_CHALLENGE_REQUIRED", "Sự kiện đang trong giờ mở bán cao điểm. Vui lòng hoàn tất xác thực Anti-Bot trước khi đặt vé.");
        }

        if (!challengePort.validateAdmissionToken(admissionToken, eventId)) {
            throw new ApiException(403, "INVALID_ADMISSION_TOKEN", "Mã xác thực Anti-Bot không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.");
        }
    }
}
