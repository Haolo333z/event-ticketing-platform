package com.haolo.ticketing.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.admin.AdminModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@Service
public class AdminRefundService {

    private final JdbcTemplate jdbc;

    public AdminRefundService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RefundItemDto> listRefunds(String status) {
        String sql =
            "SELECT r.id, r.booking_id, r.payment_attempt_id, r.amount_vnd, r.reason, r.status, r.created_at, r.settled_at, " +
            "COALESCE(u.email, '') AS customer_email, COALESCE(u.display_name, 'Khách hàng') AS customer_name " +
            "FROM ticketing.refund_attempts r " +
            "JOIN ticketing.bookings b ON b.id = r.booking_id " +
            "LEFT JOIN ticketing.users u ON u.id = b.account_id ";

        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql += "WHERE r.status = ? ";
            params.add(status.trim().toUpperCase());
        }
        sql += "ORDER BY r.created_at DESC";

        return jdbc.query(sql, (rs, rowNum) -> {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp settledAt = rs.getTimestamp("settled_at");
            return new RefundItemDto(
                rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getObject("payment_attempt_id", UUID.class),
                rs.getLong("amount_vnd"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getString("customer_email"),
                rs.getString("customer_name"),
                createdAt != null ? createdAt.toInstant().toString() : null,
                settledAt != null ? settledAt.toInstant().toString() : null
            );
        }, params.toArray());
    }

    @Transactional
    public ProcessRefundResult processRefund(UUID refundId, ProcessRefundRequest req, DemoPrincipal principal) {
        if (principal == null || !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ quản trị viên mới có quyền xử lý hoàn tiền");
        }

        var rows = jdbc.queryForList(
            "SELECT r.id, r.booking_id, r.amount_vnd, r.status, b.account_id, u.email, u.display_name " +
            "FROM ticketing.refund_attempts r " +
            "JOIN ticketing.bookings b ON b.id = r.booking_id " +
            "LEFT JOIN ticketing.users u ON u.id = b.account_id " +
            "WHERE r.id = ?",
            refundId
        );

        if (rows.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy yêu cầu hoàn tiền");
        }

        var row = rows.getFirst();
        String currentStatus = (String) row.get("status");
        if ("COMPLETED".equalsIgnoreCase(currentStatus)) {
            throw new ApiException(400, "ALREADY_COMPLETED", "Yêu cầu hoàn tiền này đã được hoàn tất trước đó");
        }

        String decision = (req != null && req.decision() != null) ? req.decision().trim().toUpperCase() : "APPROVE";
        String targetStatus = "REJECT".equals(decision) ? "FAILED" : "COMPLETED";
        UUID bookingId = (UUID) row.get("booking_id");
        Long amountVnd = (Long) row.get("amount_vnd");
        String customerEmail = (String) row.get("email");
        String customerName = (String) row.get("display_name");

        Instant now = Instant.now();
        jdbc.update(
            "UPDATE ticketing.refund_attempts SET status = ?, settled_at = clock_timestamp() WHERE id = ?",
            targetStatus, refundId
        );

        // Record in email_notifications (outbox log)
        String subject = "COMPLETED".equals(targetStatus)
            ? "EventFlow — Thông báo hoàn tiền vé thành công"
            : "EventFlow — Thông báo từ chối hoàn tiền vé";
        String payloadJson = "{\"refundId\":\"" + refundId + "\",\"amountVnd\":" + amountVnd + ",\"decision\":\"" + decision + "\",\"recipient\":\"" + customerEmail + "\"}";

        if (customerEmail != null && !customerEmail.isBlank()) {
            jdbc.update(
                "INSERT INTO ticketing.email_notifications (id, booking_id, recipient_email, subject, template_type, payload_json, status, sent_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, 'REFUND_PROCESSED', ?::jsonb, 'SENT', clock_timestamp())",
                bookingId, customerEmail, subject, payloadJson
            );
        }

        // Record in audit_logs
        jdbc.update(
            "INSERT INTO ticketing.audit_logs (actor_id, actor_role, action, target_type, target_id, payload_diff) " +
            "VALUES (?, 'ADMIN', 'PROCESS_REFUND', 'REFUND', ?, ?::jsonb)",
            principal.accountId(),
            refundId.toString(),
            "{\"decision\":\"" + decision + "\",\"targetStatus\":\"" + targetStatus + "\",\"amountVnd\":" + amountVnd + "}"
        );

        String message = "COMPLETED".equals(targetStatus)
            ? "Đã duyệt hoàn tiền thành công số tiền " + amountVnd + " VND cho khách hàng."
            : "Đã từ chối yêu cầu hoàn tiền.";

        return new ProcessRefundResult(refundId, targetStatus, now.toString(), message);
    }
}
