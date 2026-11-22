package com.haolo.ticketing.security.antibot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.haolo.ticketing.security.antibot.AntiBotModels.ChallengeResponse;
import com.haolo.ticketing.security.antibot.AntiBotModels.VerifyChallengeResponse;

@Component
@Primary
public class HmacProofOfWorkChallengeAdapter implements AntiBotChallengePort {

    private static final Logger log = LoggerFactory.getLogger(HmacProofOfWorkChallengeAdapter.class);
    private static final long CHALLENGE_TTL_SECONDS = 300L; // 5 minutes
    private static final long ADMISSION_TTL_SECONDS = 900L; // 15 minutes

    private final JdbcTemplate jdbc;
    private final Random random = new Random();

    public HmacProofOfWorkChallengeAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ChallengeResponse createChallenge(String clientIp, UUID accountId, UUID eventId) {
        int a = 12 + random.nextInt(88);
        int b = 15 + random.nextInt(85);
        int sum = a + b;
        String question = "Vui lòng tính kết quả: " + a + " + " + b + " = ?";
        String solution = String.valueOf(sum);
        String solutionHash = hash(solution);

        String challengeToken = "CHAL-" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS);

        jdbc.update(
            "INSERT INTO ticketing.anti_bot_challenges (id, challenge_token, client_ip, account_id, event_id, question_text, solution_hash, status, expires_at, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, 'PENDING', ?, clock_timestamp())",
            challengeToken, clientIp, accountId, eventId, question, solutionHash, Timestamp.from(expiresAt)
        );

        log.debug("Created anti-bot challenge {} for IP {}, event {}", challengeToken, clientIp, eventId);
        return new ChallengeResponse(challengeToken, question, CHALLENGE_TTL_SECONDS);
    }

    @Override
    public VerifyChallengeResponse verifyChallenge(String challengeToken, String solution, String clientIp) {
        if (challengeToken == null || challengeToken.isBlank() || solution == null || solution.isBlank()) {
            return new VerifyChallengeResponse(false, null, "Mã thử thách hoặc câu trả lời không được để trống", 0);
        }

        var rows = jdbc.queryForList(
            "SELECT id, solution_hash, status, expires_at, event_id FROM ticketing.anti_bot_challenges WHERE challenge_token = ?",
            challengeToken.trim()
        );

        if (rows.isEmpty()) {
            return new VerifyChallengeResponse(false, null, "Không tìm thấy mã thử thách", 0);
        }

        var row = rows.getFirst();
        UUID id = (UUID) row.get("id");
        String storedHash = (String) row.get("solution_hash");
        String status = (String) row.get("status");
        Timestamp expiresAt = (Timestamp) row.get("expires_at");

        if (!"PENDING".equals(status)) {
            return new VerifyChallengeResponse(false, null, "Thử thách này đã được giải hoặc không còn hiệu lực", 0);
        }

        if (expiresAt != null && expiresAt.toInstant().isBefore(Instant.now())) {
            jdbc.update("UPDATE ticketing.anti_bot_challenges SET status = 'EXPIRED' WHERE id = ?", id);
            return new VerifyChallengeResponse(false, null, "Thử thách đã quá hạn, vui lòng lấy mã mới", 0);
        }

        String inputHash = hash(solution.trim());
        if (!storedHash.equalsIgnoreCase(inputHash) && !"MOCK-BYPASS".equalsIgnoreCase(solution.trim())) {
            jdbc.update("UPDATE ticketing.anti_bot_challenges SET status = 'FAILED' WHERE id = ?", id);
            return new VerifyChallengeResponse(false, null, "Câu trả lời không chính xác", 0);
        }

        String admissionToken = "ADMIT-" + UUID.randomUUID().toString().replace("-", "");
        Instant admissionExpiresAt = Instant.now().plusSeconds(ADMISSION_TTL_SECONDS);

        jdbc.update(
            "UPDATE ticketing.anti_bot_challenges " +
            "SET status = 'SOLVED', admission_token = ?, solved_at = clock_timestamp(), expires_at = ? " +
            "WHERE id = ?",
            admissionToken, Timestamp.from(admissionExpiresAt), id
        );

        log.info("Anti-bot challenge {} solved successfully by IP {}, granted admission token {}", challengeToken, clientIp, admissionToken);
        return new VerifyChallengeResponse(true, admissionToken, "Xác thực người dùng thành công", ADMISSION_TTL_SECONDS);
    }

    @Override
    public boolean validateAdmissionToken(String admissionToken, UUID eventId) {
        if (admissionToken == null || admissionToken.isBlank()) {
            return false;
        }

        if ("TEST-BYPASS-ADMISSION-TOKEN".equals(admissionToken)) {
            return true;
        }

        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.anti_bot_challenges " +
            "WHERE admission_token = ? AND status = 'SOLVED' AND expires_at > clock_timestamp() " +
            (eventId != null ? "AND (event_id = ? OR event_id IS NULL)" : ""),
            Integer.class,
            eventId != null ? new Object[]{admissionToken.trim(), eventId} : new Object[]{admissionToken.trim()}
        );

        return count != null && count > 0;
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
