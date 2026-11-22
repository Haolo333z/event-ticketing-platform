package com.haolo.ticketing.organizer.payouts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.EventSecurityService;
import com.haolo.ticketing.organizer.payouts.PayoutModels.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class OrganizerPayoutService {

    private static final Logger log = LoggerFactory.getLogger(OrganizerPayoutService.class);

    private final JdbcTemplate jdbc;
    private final EventSecurityService securityService;

    public OrganizerPayoutService(JdbcTemplate jdbc, EventSecurityService securityService) {
        this.jdbc = jdbc;
        this.securityService = securityService;
    }

    public PayoutSummaryDto getPayoutSummary(DemoPrincipal principal, UUID eventId) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        if (eventId == null) throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã sự kiện");

        // Verify ownership
        if (!principal.hasRole("ADMIN")) {
            securityService.assertEventOwner(eventId, principal);
        }

        var eventRows = jdbc.queryForList(
            "SELECT e.id, e.title, e.organizer_id, COALESCE(op.saas_plan, 'PROFESSIONAL') AS saas_plan " +
            "FROM ticketing.events e " +
            "LEFT JOIN ticketing.organizer_profiles op ON e.organizer_id = op.id " +
            "WHERE e.id = ?",
            eventId
        );
        if (eventRows.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy sự kiện");
        }
        var event = eventRows.getFirst();
        String title = (String) event.get("title");
        String saasPlan = (String) event.get("saas_plan");

        double feePercent = switch (saasPlan.toUpperCase()) {
            case "STARTER" -> 8.00;
            case "ENTERPRISE" -> 3.00;
            default -> 5.00; // PROFESSIONAL
        };

        // Compute gross ticket revenue from confirmed bookings
        Long totalRevenue = jdbc.queryForObject(
            "SELECT COALESCE(SUM(total_amount_vnd), 0) FROM ticketing.bookings WHERE event_id = ? AND status = 'CONFIRMED'",
            Long.class,
            eventId
        );
        long grossRevenue = totalRevenue != null ? totalRevenue : 0L;

        Long totalSold = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.bookings WHERE event_id = ? AND status = 'CONFIRMED'",
            Long.class,
            eventId
        );
        long ticketsSold = totalSold != null ? totalSold : 0L;

        // Subtract refunds (both completed and pending)
        Long totalRefunds = jdbc.queryForObject(
            "SELECT COALESCE(SUM(ra.amount_vnd), 0) FROM ticketing.refund_attempts ra " +
            "JOIN ticketing.bookings b ON ra.booking_id = b.id " +
            "WHERE b.event_id = ? AND ra.status IN ('COMPLETED', 'PENDING')",
            Long.class,
            eventId
        );
        long refundAmount = totalRefunds != null ? totalRefunds : 0L;
        long netGrossRevenue = Math.max(0, grossRevenue - refundAmount);

        long platformFee = Math.round(netGrossRevenue * feePercent / 100.0);
        long netPayoutTotal = Math.max(0, netGrossRevenue - platformFee);

        // Sum already requested/approved/paid payouts
        Long requestedPayouts = jdbc.queryForObject(
            "SELECT COALESCE(SUM(net_payout_vnd), 0) FROM ticketing.organizer_payouts WHERE event_id = ? AND status IN ('REQUESTED', 'APPROVED', 'PAID')",
            Long.class,
            eventId
        );
        long totalRequested = requestedPayouts != null ? requestedPayouts : 0L;
        long availableBalance = Math.max(0, netPayoutTotal - totalRequested);

        return new PayoutSummaryDto(
            eventId,
            title,
            saasPlan,
            feePercent,
            netGrossRevenue,
            ticketsSold,
            platformFee,
            netPayoutTotal,
            totalRequested,
            availableBalance
        );
    }

    @Transactional
    public OrganizerPayoutDto requestPayout(DemoPrincipal principal, RequestPayoutRequest req) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        if (req == null) throw new ApiException(400, "VALIDATION_ERROR", "Dữ liệu yêu cầu rút tiền không được để trống");
        if (req.eventId() == null) throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã sự kiện");
        if (req.amountVnd() <= 0) throw new ApiException(400, "VALIDATION_ERROR", "Số tiền rút phải lớn hơn 0");
        if (req.bankName() == null || req.bankName().isBlank()) throw new ApiException(400, "VALIDATION_ERROR", "Tên ngân hàng không được để trống");
        if (req.bankAccountNumber() == null || req.bankAccountNumber().isBlank()) throw new ApiException(400, "VALIDATION_ERROR", "Số tài khoản không được để trống");
        if (req.bankAccountHolder() == null || req.bankAccountHolder().isBlank()) throw new ApiException(400, "VALIDATION_ERROR", "Tên chủ tài khoản không được để trống");

        UUID organizerProfileId = securityService.getOrganizerProfileId(principal.accountId());
        if (organizerProfileId == null && !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chưa kích hoạt hồ sơ Nhà tổ chức");
        }

        // Lock event to prevent concurrent double-withdrawal race conditions
        jdbc.queryForList("SELECT id FROM ticketing.events WHERE id = ? FOR UPDATE", req.eventId());

        PayoutSummaryDto summary = getPayoutSummary(principal, req.eventId());
        if (req.amountVnd() > summary.availableBalanceVnd()) {
            throw new ApiException(400, "INSUFFICIENT_PAYOUT_BALANCE",
                "Số tiền yêu cầu rút (" + String.format("%,d", req.amountVnd()) + "đ) vượt quá số dư khả dụng (" +
                String.format("%,d", summary.availableBalanceVnd()) + "đ)");
        }

        double feePercent = summary.platformFeePercent();
        long netPayout = req.amountVnd();
        // gross = net / (1 - fee%)
        long grossProportional = Math.round(netPayout / (1.0 - (feePercent / 100.0)));
        long platformFeeProportional = grossProportional - netPayout;

        UUID payoutId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ticketing.organizer_payouts (id, organizer_id, event_id, gross_revenue_vnd, " +
            "platform_fee_percent, platform_fee_vnd, net_payout_vnd, bank_name, bank_account_number, bank_account_holder, status, requested_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REQUESTED', clock_timestamp())",
            payoutId,
            organizerProfileId,
            req.eventId(),
            grossProportional,
            feePercent,
            platformFeeProportional,
            netPayout,
            req.bankName().trim(),
            req.bankAccountNumber().trim(),
            req.bankAccountHolder().trim().toUpperCase()
        );

        // Audit log
        jdbc.update(
            "INSERT INTO ticketing.audit_logs (actor_id, actor_role, action, target_type, target_id, payload_diff) " +
            "VALUES (?, 'ORGANIZER', 'REQUEST_PAYOUT', 'ORGANIZER_PAYOUT', ?, ?::jsonb)",
            principal.accountId(),
            payoutId.toString(),
            "{\"eventId\":\"" + req.eventId() + "\",\"amountVnd\":" + netPayout + ",\"bank\":\"" + req.bankName().trim() + "\"}"
        );

        log.info("Organizer {} requested payout {} for event {}: net={}, fee={}",
            principal.accountId(), payoutId, req.eventId(), netPayout, platformFeeProportional);

        return getPayoutById(payoutId);
    }

    public List<OrganizerPayoutDto> listOrganizerPayouts(DemoPrincipal principal) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");

        String sql = "SELECT p.id, p.organizer_id, op.organization_name, p.event_id, e.title AS event_title, " +
                     "p.gross_revenue_vnd, p.platform_fee_percent, p.platform_fee_vnd, p.net_payout_vnd, " +
                     "p.bank_name, p.bank_account_number, p.bank_account_holder, p.status, p.requested_at, p.processed_at, p.admin_notes " +
                     "FROM ticketing.organizer_payouts p " +
                     "LEFT JOIN ticketing.organizer_profiles op ON p.organizer_id = op.id " +
                     "LEFT JOIN ticketing.events e ON p.event_id = e.id ";

        if (principal.hasRole("ADMIN")) {
            sql += "ORDER BY p.requested_at DESC";
            return jdbc.query(sql, this::mapPayoutDto);
        }

        UUID organizerProfileId = securityService.getOrganizerProfileId(principal.accountId());
        if (organizerProfileId == null) return Collections.emptyList();

        sql += "WHERE p.organizer_id = ? ORDER BY p.requested_at DESC";
        return jdbc.query(sql, this::mapPayoutDto, organizerProfileId);
    }

    public List<OrganizerPayoutDto> listAllPayouts(DemoPrincipal admin, String status) {
        if (admin == null || !admin.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ quản trị viên mới có quyền xem danh sách giải ngân");
        }

        String sql = "SELECT p.id, p.organizer_id, op.organization_name, p.event_id, e.title AS event_title, " +
                     "p.gross_revenue_vnd, p.platform_fee_percent, p.platform_fee_vnd, p.net_payout_vnd, " +
                     "p.bank_name, p.bank_account_number, p.bank_account_holder, p.status, p.requested_at, p.processed_at, p.admin_notes " +
                     "FROM ticketing.organizer_payouts p " +
                     "LEFT JOIN ticketing.organizer_profiles op ON p.organizer_id = op.id " +
                     "LEFT JOIN ticketing.events e ON p.event_id = e.id ";

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql += "WHERE p.status = ? ORDER BY p.requested_at DESC";
            return jdbc.query(sql, this::mapPayoutDto, status.trim().toUpperCase());
        }

        sql += "ORDER BY p.requested_at DESC";
        return jdbc.query(sql, this::mapPayoutDto);
    }

    @Transactional
    public ProcessPayoutResponse processPayout(DemoPrincipal admin, UUID payoutId, ProcessPayoutRequest req) {
        if (admin == null || !admin.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ quản trị viên mới có quyền phê duyệt giải ngân");
        }
        if (payoutId == null) throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã yêu cầu rút tiền");
        if (req == null || req.decision() == null || req.decision().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thiếu quyết định phê duyệt");
        }

        String decision = req.decision().trim().toUpperCase();
        if (!Set.of("APPROVE", "PAID", "REJECT").contains(decision)) {
            throw new ApiException(400, "INVALID_DECISION", "Quyết định không hợp lệ (hỗ trợ APPROVE, PAID, REJECT)");
        }

        var rows = jdbc.queryForList("SELECT id, status, net_payout_vnd FROM ticketing.organizer_payouts WHERE id = ? FOR UPDATE", payoutId);
        if (rows.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy yêu cầu rút tiền");
        }
        var row = rows.getFirst();
        String currentStatus = (String) row.get("status");

        String newStatus = switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "PAID" -> "PAID";
            case "REJECT" -> "REJECTED";
            default -> currentStatus;
        };

        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);

        if ("PAID".equals(newStatus) || "REJECTED".equals(newStatus)) {
            jdbc.update(
                "UPDATE ticketing.organizer_payouts SET status = ?, processed_at = ?, admin_notes = ? WHERE id = ?",
                newStatus, nowTs, req.adminNotes(), payoutId
            );
        } else {
            jdbc.update(
                "UPDATE ticketing.organizer_payouts SET status = ?, admin_notes = ? WHERE id = ?",
                newStatus, req.adminNotes(), payoutId
            );
        }

        // Audit log
        jdbc.update(
            "INSERT INTO ticketing.audit_logs (actor_id, actor_role, action, target_type, target_id, payload_diff) " +
            "VALUES (?, 'ADMIN', 'PROCESS_PAYOUT', 'ORGANIZER_PAYOUT', ?, ?::jsonb)",
            admin.accountId(),
            payoutId.toString(),
            "{\"decision\":\"" + decision + "\",\"newStatus\":\"" + newStatus + "\"}"
        );

        log.info("Admin {} processed payout {}: decision={}, newStatus={}", admin.accountId(), payoutId, decision, newStatus);

        return new ProcessPayoutResponse(
            payoutId,
            newStatus,
            now,
            "Cập nhật trạng thái yêu cầu rút tiền thành công: " + newStatus
        );
    }

    public OrganizerPayoutDto getPayoutById(UUID id) {
        String sql = "SELECT p.id, p.organizer_id, op.organization_name, p.event_id, e.title AS event_title, " +
                     "p.gross_revenue_vnd, p.platform_fee_percent, p.platform_fee_vnd, p.net_payout_vnd, " +
                     "p.bank_name, p.bank_account_number, p.bank_account_holder, p.status, p.requested_at, p.processed_at, p.admin_notes " +
                     "FROM ticketing.organizer_payouts p " +
                     "LEFT JOIN ticketing.organizer_profiles op ON p.organizer_id = op.id " +
                     "LEFT JOIN ticketing.events e ON p.event_id = e.id " +
                     "WHERE p.id = ?";
        var list = jdbc.query(sql, this::mapPayoutDto, id);
        return list.isEmpty() ? null : list.getFirst();
    }

    private OrganizerPayoutDto mapPayoutDto(ResultSet rs, int rowNum) throws SQLException {
        Timestamp reqAt = rs.getTimestamp("requested_at");
        Timestamp procAt = rs.getTimestamp("processed_at");
        return new OrganizerPayoutDto(
            rs.getObject("id", UUID.class),
            rs.getObject("organizer_id", UUID.class),
            rs.getString("organization_name") != null ? rs.getString("organization_name") : "Đối tác EventFlow",
            rs.getObject("event_id", UUID.class),
            rs.getString("event_title") != null ? rs.getString("event_title") : "Sự kiện",
            rs.getLong("gross_revenue_vnd"),
            rs.getDouble("platform_fee_percent"),
            rs.getLong("platform_fee_vnd"),
            rs.getLong("net_payout_vnd"),
            rs.getString("bank_name"),
            rs.getString("bank_account_number"),
            rs.getString("bank_account_holder"),
            rs.getString("status"),
            reqAt != null ? reqAt.toInstant() : null,
            procAt != null ? procAt.toInstant() : null,
            rs.getString("admin_notes")
        );
    }
}
