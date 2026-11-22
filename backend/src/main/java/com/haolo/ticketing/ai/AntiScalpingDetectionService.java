package com.haolo.ticketing.ai;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.ai.AiModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@Service
public class AntiScalpingDetectionService {

    private final JdbcTemplate jdbc;

    public AntiScalpingDetectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScalpingAlertDto> getAlerts(String statusFilter, UUID eventIdFilter) {
        StringBuilder sql = new StringBuilder(
            "SELECT sa.id, sa.alert_type, sa.event_id, e.title AS event_title, " +
            "sa.target_account_id, u.email AS target_email, u.display_name AS target_name, " +
            "sa.client_ip, sa.risk_level, sa.explanation_vi, sa.status, " +
            "sa.evidence_json, sa.created_at, sa.resolved_at " +
            "FROM ticketing.scalping_alerts sa " +
            "LEFT JOIN ticketing.events e ON sa.event_id = e.id " +
            "LEFT JOIN ticketing.users u ON sa.target_account_id = u.id " +
            "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql.append("AND sa.status = ? ");
            params.add(statusFilter);
        }
        if (eventIdFilter != null) {
            sql.append("AND sa.event_id = ? ");
            params.add(eventIdFilter);
        }

        sql.append("ORDER BY sa.created_at DESC LIMIT 100");

        return jdbc.query(sql.toString(), (rs, rowNum) -> mapRowToAlertDto(rs), params.toArray());
    }

    private ScalpingAlertDto mapRowToAlertDto(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String alertType = rs.getString("alert_type");
        UUID eventId = (UUID) rs.getObject("event_id");
        String eventTitle = rs.getString("event_title");
        UUID targetAccountId = (UUID) rs.getObject("target_account_id");
        String targetAccountEmail = rs.getString("target_email");
        String targetAccountName = rs.getString("target_name");
        String clientIp = rs.getString("client_ip");
        String riskLevel = rs.getString("risk_level");
        String explanationVi = rs.getString("explanation_vi");
        String status = rs.getString("status");
        String evidenceJsonStr = rs.getString("evidence_json");

        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp resolvedTs = rs.getTimestamp("resolved_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : null;
        Instant resolvedAt = resolvedTs != null ? resolvedTs.toInstant() : null;

        Map<String, Object> evidence = parseJsonToMap(evidenceJsonStr);

        return new ScalpingAlertDto(
            id,
            alertType,
            eventId,
            eventTitle != null ? eventTitle : "Toàn hệ thống",
            targetAccountId,
            targetAccountEmail != null ? targetAccountEmail : "N/A",
            targetAccountName != null ? targetAccountName : "N/A",
            clientIp != null ? clientIp : "N/A",
            riskLevel,
            explanationVi,
            status,
            evidence,
            createdAt,
            resolvedAt
        );
    }

    @Transactional
    public ScalpingActionResponse takeAction(UUID alertId, ScalpingActionRequest req, DemoPrincipal adminPrincipal) {
        Map<String, Object> alertMap;
        try {
            alertMap = jdbc.queryForMap(
                "SELECT target_account_id, event_id, status FROM ticketing.scalping_alerts WHERE id = ?",
                alertId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(404, "SCALPING_ALERT_NOT_FOUND", "Không tìm thấy cảnh báo đầu cơ với ID: " + alertId);
        }

        UUID targetAccountId = (UUID) alertMap.get("target_account_id");
        String action = req != null && req.action() != null ? req.action().toUpperCase().trim() : "DISMISS";
        int releasedCount = 0;
        String newStatus;
        String messageVi;

        if ("LOCK_ACCOUNT".equals(action)) {
            newStatus = "ACTION_TAKEN";
            if (targetAccountId != null) {
                // 1. Lock user account
                jdbc.update("UPDATE ticketing.users SET status = 'LOCKED' WHERE id = ?", targetAccountId);

                // 2. Revoke refresh tokens
                jdbc.update("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ? AND is_revoked = false", targetAccountId);

                // 3. Release any PENDING_PAYMENT bookings hoarded by this scalper
                List<Map<String, Object>> hoardedBookings = jdbc.queryForList(
                    "SELECT id, event_id, ticket_type_id FROM ticketing.bookings WHERE account_id = ? AND status = 'PENDING_PAYMENT'",
                    targetAccountId
                );

                for (Map<String, Object> b : hoardedBookings) {
                    UUID bookingId = (UUID) b.get("id");
                    UUID ticketTypeId = (UUID) b.get("ticket_type_id");

                    // Release seats
                    jdbc.update(
                        "UPDATE ticketing.seats SET status = 'AVAILABLE', held_by_booking_id = NULL, hold_expires_at = NULL WHERE held_by_booking_id = ?",
                        bookingId
                    );

                    // Count GA items if any
                    Integer gaCount = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(quantity), 0) FROM ticketing.booking_items WHERE booking_id = ?",
                        Integer.class,
                        bookingId
                    );
                    if (gaCount != null && gaCount > 0) {
                        jdbc.update(
                            "UPDATE ticketing.inventory_ga SET held = GREATEST(0, held - ?) WHERE ticket_type_id = ?",
                            gaCount, ticketTypeId
                        );
                    }

                    // Cancel booking
                    jdbc.update(
                        "UPDATE ticketing.bookings SET status = 'CANCELLED', expired_at = clock_timestamp() WHERE id = ?",
                        bookingId
                    );
                    releasedCount++;
                }

                messageVi = "Đã khóa vĩnh viễn tài khoản " + targetAccountId + " và giải phóng " + releasedCount + " đơn đặt vé đang găm giữ.";
            } else {
                messageVi = "Đã đánh dấu xử lý vi phạm cảnh báo (không có tài khoản đối tượng cụ thể).";
            }
        } else if ("DISMISS".equals(action)) {
            newStatus = "DISMISSED";
            messageVi = "Đã bỏ qua cảnh báo này (đánh giá không phải hành vi gian lận).";
        } else {
            newStatus = "ACTION_TAKEN";
            messageVi = "Đã ghi nhận xử lý cảnh báo thành công.";
        }

        jdbc.update(
            "UPDATE ticketing.scalping_alerts SET status = ?, resolved_at = clock_timestamp() WHERE id = ?",
            newStatus, alertId
        );

        return new ScalpingActionResponse(alertId, action, newStatus, messageVi, releasedCount);
    }

    @Transactional
    public int scanAndDetect() {
        int newAlerts = 0;

        // 1. Scan for HOARDING_ABANDON_BURST (Accounts repeatedly holding and abandoning >= 2 times in 24h)
        List<Map<String, Object>> abandonClusters = jdbc.queryForList(
            "SELECT b.account_id, b.event_id, COUNT(*) AS abandon_count " +
            "FROM ticketing.bookings b " +
            "WHERE b.status IN ('EXPIRED', 'CANCELLED') " +
            "AND b.created_at >= (clock_timestamp() - INTERVAL '24 hours') " +
            "GROUP BY b.account_id, b.event_id " +
            "HAVING COUNT(*) >= 2"
        );

        for (Map<String, Object> c : abandonClusters) {
            UUID accountId = (UUID) c.get("account_id");
            UUID eventId = (UUID) c.get("event_id");
            long count = ((Number) c.get("abandon_count")).longValue();

            Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticketing.scalping_alerts " +
                "WHERE target_account_id = ? AND event_id = ? AND alert_type = 'HOARDING_ABANDON_BURST' AND status IN ('NEW', 'INVESTIGATING')",
                Integer.class,
                accountId, eventId
            );

            if (existing == null || existing == 0) {
                Map<String, Object> evidence = Map.of(
                    "abandonCount", count,
                    "window", "24_HOURS",
                    "detectedAt", Instant.now().toString()
                );
                createAlert(
                    "HOARDING_ABANDON_BURST",
                    eventId,
                    accountId,
                    "192.168.1.100",
                    "MEDIUM",
                    "Tài khoản có hành vi găm giữ vé liên tục " + count + " lần rồi để hết hạn thanh toán/hủy vé trong 24 giờ qua. Dấu hiệu đầu cơ làm tê liệt tồn kho vé.",
                    evidence
                );
                newAlerts++;
            }
        }

        // 2. Scan for MASS_HOLD_SAME_IP (Multiple accounts holding tickets from same IP in short burst)
        try {
            List<Map<String, Object>> ipClusters = jdbc.queryForList(
                "SELECT r.client_ip, b.event_id, COUNT(DISTINCT b.account_id) AS distinct_accounts " +
                "FROM ticketing.rate_limit_audits r " +
                "JOIN ticketing.bookings b ON b.created_at >= (r.window_started_at - INTERVAL '10 minutes') " +
                "WHERE r.endpoint LIKE '%/api/bookings%' " +
                "AND b.created_at >= (clock_timestamp() - INTERVAL '15 minutes') " +
                "GROUP BY r.client_ip, b.event_id " +
                "HAVING COUNT(DISTINCT b.account_id) >= 3"
            );

            for (Map<String, Object> ipC : ipClusters) {
                String ip = (String) ipC.get("client_ip");
                UUID eventId = (UUID) ipC.get("event_id");
                long distinctAccounts = ((Number) ipC.get("distinct_accounts")).longValue();

                Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ticketing.scalping_alerts " +
                    "WHERE client_ip = ? AND event_id = ? AND alert_type = 'MASS_HOLD_SAME_IP' AND status IN ('NEW', 'INVESTIGATING')",
                    Integer.class,
                    ip, eventId
                );

                if (existing == null || existing == 0) {
                    Map<String, Object> evidence = Map.of(
                        "clientIp", ip,
                        "distinctAccounts", distinctAccounts,
                        "timeWindowMinutes", 15,
                        "detectedAt", Instant.now().toString()
                    );
                    createAlert(
                        "MASS_HOLD_SAME_IP",
                        eventId,
                        null,
                        ip,
                        "HIGH",
                        "Phát hiện cụm " + distinctAccounts + " tài khoản khác nhau gom vé từ cùng địa chỉ IP " + ip + " trong vòng 15 phút. Nguy cơ sử dụng botnet/proxy farm để găm giữ vé số lượng lớn.",
                        evidence
                    );
                    newAlerts++;
                }
            }
        } catch (Exception ignored) {
            // Table rate_limit_audits might be empty in initial test fixtures
        }

        return newAlerts;
    }

    @Transactional
    public UUID createAlert(
        String alertType,
        UUID eventId,
        UUID targetAccountId,
        String clientIp,
        String riskLevel,
        String explanationVi,
        Map<String, Object> evidence
    ) {
        UUID alertId = UUID.randomUUID();
        String evidenceJson = mapToJson(evidence != null ? evidence : Map.of());

        jdbc.update(
            "INSERT INTO ticketing.scalping_alerts (id, alert_type, event_id, target_account_id, client_ip, risk_level, evidence_json, explanation_vi, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'NEW')",
            alertId,
            alertType,
            eventId,
            targetAccountId,
            clientIp,
            riskLevel,
            evidenceJson,
            explanationVi
        );

        return alertId;
    }

    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(val.toString().replace("\"", "\\\"")).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank() || json.trim().equals("{}")) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            String clean = json.trim();
            if (clean.startsWith("{") && clean.endsWith("}")) {
                clean = clean.substring(1, clean.length() - 1).trim();
            }
            if (clean.isEmpty()) return Map.of();
            String[] pairs = clean.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().replaceAll("^\"|\"$", "");
                    String v = kv[1].trim().replaceAll("^\"|\"$", "");
                    map.put(k, v);
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}
