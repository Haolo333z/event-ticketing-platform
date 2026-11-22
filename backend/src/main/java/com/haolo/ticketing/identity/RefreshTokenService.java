package com.haolo.ticketing.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.shared.ApiException;

@Service
public class RefreshTokenService {
    private final JdbcTemplate jdbc;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long validityDays;

    public RefreshTokenService(
        JdbcTemplate jdbc,
        @Value("${demo.jwt.refresh-token-validity-days:7}") long validityDays
    ) {
        this.jdbc = jdbc;
        this.validityDays = validityDays;
    }

    public record TokenRecord(UUID id, UUID userId, UUID familyId, boolean isRevoked, Instant expiresAt, UUID replacedBy) {}
    public record RotationResult(UUID userId, String newRawToken) {}
    public record ActiveSessionDto(UUID familyId, String userAgent, String ipAddress, Instant createdAt, Instant expiresAt, boolean current) {}

    @Transactional
    public String createRefreshToken(UUID userId) {
        return createRefreshToken(userId, null, null);
    }

    @Transactional
    public String createRefreshToken(UUID userId, String userAgent, String ipAddress) {
        UUID familyId = UUID.randomUUID();
        String rawToken = generateRandomHex(32);
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(validityDays, ChronoUnit.DAYS);

        jdbc.update(
            "INSERT INTO ticketing.refresh_tokens(id, user_id, token_hash, family_id, is_revoked, expires_at, user_agent, ip_address) VALUES (?, ?, ?, ?, false, ?, ?, ?)",
            UUID.randomUUID(), userId, tokenHash, familyId, java.sql.Timestamp.from(expiresAt), userAgent, ipAddress
        );
        return rawToken;
    }

    @Transactional
    public RotationResult rotateRefreshToken(String rawToken) {
        return rotateRefreshToken(rawToken, null, null);
    }

    @Transactional
    public RotationResult rotateRefreshToken(String rawToken, String userAgent, String ipAddress) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(401, "INVALID_REFRESH_TOKEN", "Refresh token không được để trống");
        }

        String tokenHash = hashToken(rawToken);
        var tokens = jdbc.query(
            "SELECT id, user_id, family_id, is_revoked, expires_at, replaced_by FROM ticketing.refresh_tokens WHERE token_hash = ?",
            (rs, rowNum) -> new TokenRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getBoolean("is_revoked"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getObject("replaced_by", UUID.class)
            ),
            tokenHash
        );

        if (tokens.isEmpty()) {
            throw new ApiException(401, "INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ hoặc không tồn tại");
        }

        TokenRecord current = tokens.getFirst();

        // REUSE DETECTION: If token was already revoked or replaced, revoke entire family!
        if (current.isRevoked() || current.replacedBy() != null) {
            jdbc.update("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE family_id = ?", current.familyId());
            throw new ApiException(401, "TOKEN_REUSE_DETECTED", "Phát hiện tái sử dụng phiên làm việc. Toàn bộ chuỗi phiên đã bị thu hồi vì lý do an toàn. Vui lòng đăng nhập lại.");
        }

        if (Instant.now().isAfter(current.expiresAt())) {
            throw new ApiException(401, "REFRESH_TOKEN_EXPIRED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }

        // Generate new token in the same family
        UUID newId = UUID.randomUUID();
        String newRawToken = generateRandomHex(32);
        String newTokenHash = hashToken(newRawToken);
        Instant newExpiresAt = Instant.now().plus(validityDays, ChronoUnit.DAYS);

        // Insert new token first so foreign key constraint (replaced_by -> id) is satisfied
        jdbc.update(
            "INSERT INTO ticketing.refresh_tokens(id, user_id, token_hash, family_id, is_revoked, expires_at, user_agent, ip_address) VALUES (?, ?, ?, ?, false, ?, ?, ?)",
            newId, current.userId(), newTokenHash, current.familyId(), java.sql.Timestamp.from(newExpiresAt), userAgent, ipAddress
        );

        // Mark current token replaced
        jdbc.update(
            "UPDATE ticketing.refresh_tokens SET is_revoked = true, replaced_by = ? WHERE id = ?",
            newId, current.id()
        );

        return new RotationResult(current.userId(), newRawToken);
    }

    public List<ActiveSessionDto> getActiveSessions(UUID userId, UUID currentFamilyId) {
        String sql = """
            SELECT DISTINCT ON (family_id) family_id, user_agent, ip_address, created_at, expires_at
            FROM ticketing.refresh_tokens
            WHERE user_id = ? AND is_revoked = false AND expires_at > NOW()
            ORDER BY family_id, created_at DESC
            """;
        return jdbc.query(sql, (rs, rowNum) -> {
            UUID familyId = rs.getObject("family_id", UUID.class);
            return new ActiveSessionDto(
                familyId,
                rs.getString("user_agent"),
                rs.getString("ip_address"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                currentFamilyId != null && currentFamilyId.equals(familyId)
            );
        }, userId);
    }

    public UUID findFamilyIdByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        String tokenHash = hashToken(rawToken);
        var families = jdbc.query(
            "SELECT family_id FROM ticketing.refresh_tokens WHERE token_hash = ?",
            (rs, rowNum) -> rs.getObject("family_id", UUID.class),
            tokenHash
        );
        return families.isEmpty() ? null : families.getFirst();
    }

    @Transactional
    public void revokeFamily(UUID userId, UUID familyId) {
        jdbc.update("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ? AND family_id = ?", userId, familyId);
    }

    @Transactional
    public void revokeOtherFamilies(UUID userId, UUID currentFamilyId) {
        if (currentFamilyId == null) {
            revokeAllForUser(userId);
            return;
        }
        jdbc.update("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ? AND family_id <> ?", userId, currentFamilyId);
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        String tokenHash = hashToken(rawToken);
        var tokens = jdbc.query(
            "SELECT family_id FROM ticketing.refresh_tokens WHERE token_hash = ?",
            (rs, rowNum) -> rs.getObject("family_id", UUID.class),
            tokenHash
        );
        if (!tokens.isEmpty()) {
            jdbc.update("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE family_id = ?", tokens.getFirst());
        }
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        jdbc.update("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ?", userId);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String generateRandomHex(int numBytes) {
        byte[] bytes = new byte[numBytes];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(numBytes * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
