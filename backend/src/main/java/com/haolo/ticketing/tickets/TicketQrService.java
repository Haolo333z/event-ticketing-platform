package com.haolo.ticketing.tickets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@Service
public class TicketQrService {

    private final JdbcTemplate jdbc;
    private final String qrSecret;

    public TicketQrService(
            JdbcTemplate jdbc,
            @Value("${eventflow.ticket.qr-secret:eventflow-super-secure-hmac-sha256-qr-signing-key-secret-2026}") String qrSecret) {
        this.jdbc = jdbc;
        this.qrSecret = qrSecret;
    }

    public String generateSignedQrToken(UUID ticketId, String ticketCode, UUID eventId, Instant issuedAt) {
        if (ticketId == null || ticketCode == null || eventId == null || issuedAt == null) {
            throw ApiException.invalid();
        }
        long epochSecond = issuedAt.getEpochSecond();
        String raw = ticketId + ":" + ticketCode + ":" + eventId + ":" + epochSecond;
        String signature = sign(raw, qrSecret);
        return ticketId + "." + ticketCode + "." + eventId + "." + epochSecond + "." + signature;
    }

    public boolean verifyQrToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 5) {
            return false;
        }
        String ticketIdStr = parts[0];
        String ticketCode = parts[1];
        String eventIdStr = parts[2];
        String epochStr = parts[3];
        String signature = parts[4];

        try {
            UUID.fromString(ticketIdStr);
            UUID.fromString(eventIdStr);
            Long.parseLong(epochStr);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String raw = ticketIdStr + ":" + ticketCode + ":" + eventIdStr + ":" + epochStr;
        try {
            byte[] expectedSig = computeHmac(raw, qrSecret);
            byte[] actualSig = Base64.getUrlDecoder().decode(signature);
            return MessageDigest.isEqual(expectedSig, actualSig);
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TicketQrResponse getTicketQr(DemoPrincipal principal, UUID ticketId) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập để xem mã vé");
        }

        var rows = jdbc.queryForList(
            "SELECT t.id, t.code, t.booking_id, b.event_id, b.account_id, b.status " +
            "FROM ticketing.tickets t " +
            "JOIN ticketing.bookings b ON b.id = t.booking_id " +
            "WHERE t.id = ?",
            ticketId
        );

        if (rows.isEmpty()) {
            throw ApiException.missing();
        }

        var row = rows.getFirst();
        UUID bookingAccountId = (UUID) row.get("account_id");
        String status = (String) row.get("status");
        UUID eventId = (UUID) row.get("event_id");
        String code = (String) row.get("code");

        if (!"CONFIRMED".equals(status)) {
            throw new ApiException(400, "BAD_REQUEST", "Vé chỉ được kích hoạt khi đơn hàng đã thanh toán thành công");
        }

        boolean isOwner = principal.accountId() != null && principal.accountId().equals(bookingAccountId);
        boolean isAdmin = principal.hasRole("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new ApiException(403, "FORBIDDEN", "Bạn không có quyền truy cập mã QR của vé này");
        }

        Instant now = Instant.now();
        String qrToken = generateSignedQrToken(ticketId, code, eventId, now);

        return new TicketQrResponse(ticketId, code, eventId, qrToken, now.toString());
    }

    private static String sign(String raw, String secret) {
        byte[] hmacBytes = computeHmac(raw, secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    }

    private static byte[] computeHmac(String raw, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
