package com.haolo.ticketing.checkin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.checkin.CheckInModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.tickets.TicketQrService;

@Service
public class StaffCheckInService {

    private final JdbcTemplate jdbc;
    private final TicketQrService ticketQrService;

    public StaffCheckInService(JdbcTemplate jdbc, TicketQrService ticketQrService) {
        this.jdbc = jdbc;
        this.ticketQrService = ticketQrService;
    }

    public List<AssignedEventDto> getAssignedEvents(DemoPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        }

        if (principal.hasRole("ADMIN")) {
            return jdbc.query(
                "SELECT e.id, e.title, e.venue, e.starts_at, NULL AS ends_at, e.status, NULL AS assigned_at " +
                "FROM ticketing.events e " +
                "ORDER BY e.starts_at DESC",
                (rs, rowNum) -> {
                    Timestamp startsAt = rs.getTimestamp("starts_at");
                    Timestamp endsAt = rs.getTimestamp("ends_at");
                    return new AssignedEventDto(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("venue"),
                        startsAt != null ? startsAt.toInstant().toString() : null,
                        endsAt != null ? endsAt.toInstant().toString() : null,
                        rs.getString("status"),
                        null
                    );
                }
            );
        }

        if (principal.hasRole("STAFF")) {
            return jdbc.query(
                "SELECT e.id, e.title, e.venue, e.starts_at, NULL AS ends_at, e.status, a.assigned_at " +
                "FROM ticketing.event_staff_assignments a " +
                "JOIN ticketing.events e ON e.id = a.event_id " +
                "WHERE a.staff_user_id = ? AND a.revoked_at IS NULL " +
                "ORDER BY e.starts_at DESC",
                (rs, rowNum) -> {
                    Timestamp startsAt = rs.getTimestamp("starts_at");
                    Timestamp endsAt = rs.getTimestamp("ends_at");
                    Timestamp assignedAt = rs.getTimestamp("assigned_at");
                    return new AssignedEventDto(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("venue"),
                        startsAt != null ? startsAt.toInstant().toString() : null,
                        endsAt != null ? endsAt.toInstant().toString() : null,
                        rs.getString("status"),
                        assignedAt != null ? assignedAt.toInstant().toString() : null
                    );
                },
                principal.accountId()
            );
        }

        throw new ApiException(403, "FORBIDDEN", "Chỉ nhân viên soát vé (STAFF) hoặc quản trị viên (ADMIN) mới có quyền truy cập");
    }

    public void assertStaffAccess(DemoPrincipal principal, UUID eventId) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        }
        if (principal.hasRole("ADMIN")) {
            return;
        }
        if (principal.hasRole("STAFF")) {
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.event_staff_assignments " +
                "WHERE staff_user_id = ? AND event_id = ? AND revoked_at IS NULL",
                Integer.class,
                principal.accountId(),
                eventId
            );
            if (count == null || count == 0) {
                throw new ApiException(403, "EVENT_SCOPE_DENIED", "Nhân viên không được phân công phụ trách soát vé sự kiện này!");
            }
            return;
        }
        throw new ApiException(403, "FORBIDDEN", "Chỉ nhân viên soát vé (STAFF) hoặc quản trị viên (ADMIN) mới có quyền truy cập");
    }

    @Transactional
    public CheckInResult checkIn(DemoPrincipal principal, UUID eventId, CheckInRequest req) {
        assertStaffAccess(principal, eventId);

        if (req == null || ((req.qrToken() == null || req.qrToken().isBlank()) && (req.ticketCode() == null || req.ticketCode().isBlank()))) {
            throw new ApiException(400, "VALIDATION_ERROR", "Cần cung cấp mã QR hoặc mã vé");
        }

        UUID targetTicketId = null;
        String targetTicketCode = null;

        if (req.qrToken() != null && !req.qrToken().isBlank()) {
            String token = req.qrToken().trim();
            if (!ticketQrService.verifyQrToken(token)) {
                throw new ApiException(400, "INVALID_QR_TOKEN", "Mã QR không hợp lệ hoặc chữ ký số bị giả mạo");
            }
            String[] parts = token.split("\\.");
            try {
                targetTicketId = UUID.fromString(parts[0]);
                targetTicketCode = parts[1];
                UUID tokenEventId = UUID.fromString(parts[2]);
                if (!eventId.equals(tokenEventId)) {
                    throw new ApiException(400, "EVENT_MISMATCH", "Vé không thuộc sự kiện đang chọn!");
                }
            } catch (IllegalArgumentException e) {
                throw new ApiException(400, "INVALID_QR_TOKEN", "Định dạng mã QR không hợp lệ");
            }
        } else {
            targetTicketCode = req.ticketCode().trim();
        }

        var rows = targetTicketId != null
            ? jdbc.queryForList(
                "SELECT t.id AS ticket_id, t.code AS ticket_code, t.status AS ticket_status, b.id AS booking_id, b.event_id, b.status AS booking_status, " +
                "u.display_name AS attendee_name, tt.name AS ticket_type_name, s.label AS seat_label " +
                "FROM ticketing.tickets t " +
                "JOIN ticketing.bookings b ON b.id = t.booking_id " +
                "JOIN ticketing.users u ON u.id = b.account_id " +
                "JOIN ticketing.booking_items bi ON bi.id = t.booking_item_id " +
                "JOIN ticketing.ticket_types tt ON tt.id = bi.ticket_type_id " +
                "LEFT JOIN ticketing.seats s ON s.id = bi.seat_id " +
                "WHERE t.id = ?",
                targetTicketId
            )
            : jdbc.queryForList(
                "SELECT t.id AS ticket_id, t.code AS ticket_code, t.status AS ticket_status, b.id AS booking_id, b.event_id, b.status AS booking_status, " +
                "u.display_name AS attendee_name, tt.name AS ticket_type_name, s.label AS seat_label " +
                "FROM ticketing.tickets t " +
                "JOIN ticketing.bookings b ON b.id = t.booking_id " +
                "JOIN ticketing.users u ON u.id = b.account_id " +
                "JOIN ticketing.booking_items bi ON bi.id = t.booking_item_id " +
                "JOIN ticketing.ticket_types tt ON tt.id = bi.ticket_type_id " +
                "LEFT JOIN ticketing.seats s ON s.id = bi.seat_id " +
                "WHERE t.code = ?",
                targetTicketCode
            );

        if (rows.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy vé trong hệ thống");
        }

        var row = rows.getFirst();
        UUID ticketId = (UUID) row.get("ticket_id");
        String ticketCode = (String) row.get("ticket_code");
        String ticketStatus = (String) row.get("ticket_status");
        UUID ticketEventId = (UUID) row.get("event_id");
        String bookingStatus = (String) row.get("booking_status");
        String attendeeName = (String) row.get("attendee_name");
        String ticketTypeName = (String) row.get("ticket_type_name");
        String seatLabel = (String) row.get("seat_label");
        if (seatLabel == null || seatLabel.isBlank()) {
            seatLabel = "Vé GA (Tự do)";
        }

        if ("REVOKED".equalsIgnoreCase(ticketStatus)) {
            throw new ApiException(400, "TICKET_REVOKED", "Vé này đã bị thu hồi do đã chuyển nhượng hoặc cấp đổi mã mới!");
        }

        // Cross-guard: Ticket listed on Resale
        Integer activeResales = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.resale_listings WHERE ticket_id = ? AND status IN ('ACTIVE', 'RESERVED')",
            Integer.class,
            ticketId
        );
        if (activeResales != null && activeResales > 0) {
            throw new ApiException(409, "TICKET_LOCKED_RESALE", "Vé đang được rao bán trên Chợ Resale, không thể soát vé!");
        }

        // Cross-guard: Ticket pending transfer
        Integer pendingTransfers = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.ticket_transfers WHERE ticket_id = ? AND status = 'PENDING' AND expires_at > clock_timestamp()",
            Integer.class,
            ticketId
        );
        if (pendingTransfers != null && pendingTransfers > 0) {
            throw new ApiException(409, "TICKET_PENDING_TRANSFER", "Vé đang trong tiến trình gửi tặng bạn bè, không thể soát vé!");
        }

        if (!eventId.equals(ticketEventId)) {
            throw new ApiException(400, "EVENT_MISMATCH", "Vé không thuộc sự kiện đang chọn!");
        }

        if (!"CONFIRMED".equals(bookingStatus)) {
            throw new ApiException(400, "TICKET_NOT_CONFIRMED", "Vé chưa hoàn tất thanh toán (trạng thái: " + bookingStatus + ")!");
        }

        String gate = (req.gateName() != null && !req.gateName().isBlank()) ? req.gateName().trim() : "MAIN_GATE";
        String deviceInfo = req.deviceInfo();
        UUID staffUserId = principal.accountId();

        Timestamp checkedInAt;
        try {
            checkedInAt = jdbc.queryForObject(
                "INSERT INTO ticketing.ticket_check_ins (id, ticket_id, event_id, staff_user_id, gate_name, device_info, checked_in_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, clock_timestamp()) " +
                "RETURNING checked_in_at",
                Timestamp.class,
                ticketId, eventId, staffUserId, gate, deviceInfo
            );
        } catch (DataIntegrityViolationException e) {
            var prevList = jdbc.queryForList(
                "SELECT c.checked_in_at, c.gate_name, u.display_name AS staff_name " +
                "FROM ticketing.ticket_check_ins c " +
                "LEFT JOIN ticketing.users u ON u.id = c.staff_user_id " +
                "WHERE c.ticket_id = ?",
                ticketId
            );
            if (!prevList.isEmpty()) {
                var prev = prevList.getFirst();
                Timestamp firstAt = (Timestamp) prev.get("checked_in_at");
                String prevGate = (String) prev.get("gate_name");
                String staff = (String) prev.get("staff_name");
                String timeStr = firstAt != null ? firstAt.toInstant().toString() : "trước đó";
                throw new ApiException(409, "TICKET_ALREADY_USED",
                    "CẢNH BÁO: Vé này đã được quét lúc " + timeStr + " tại cổng " + prevGate + (staff != null ? " bởi " + staff : "") + "!");
            } else {
                throw new ApiException(409, "TICKET_ALREADY_USED", "CẢNH BÁO: Vé này đã được quét trước đó!");
            }
        }

        return new CheckInResult(
            "CHECKED_IN",
            ticketId,
            ticketCode,
            attendeeName,
            ticketTypeName,
            seatLabel,
            checkedInAt != null ? checkedInAt.toInstant().toString() : Instant.now().toString(),
            gate,
            "HỢP LỆ — MỜI VÀO"
        );
    }

    public CheckInStats getStats(DemoPrincipal principal, UUID eventId) {
        assertStaffAccess(principal, eventId);

        Long totalSold = jdbc.queryForObject(
            "SELECT count(t.id) FROM ticketing.tickets t " +
            "JOIN ticketing.bookings b ON b.id = t.booking_id " +
            "WHERE b.event_id = ? AND b.status = 'CONFIRMED'",
            Long.class,
            eventId
        );
        long sold = totalSold != null ? totalSold : 0L;

        Long totalCheckedIn = jdbc.queryForObject(
            "SELECT count(c.id) FROM ticketing.ticket_check_ins c WHERE c.event_id = ?",
            Long.class,
            eventId
        );
        long checkedIn = totalCheckedIn != null ? totalCheckedIn : 0L;

        long remaining = Math.max(0, sold - checkedIn);
        double rate = sold > 0 ? Math.round((double) checkedIn * 1000.0 / sold) / 10.0 : 0.0;

        return new CheckInStats(sold, checkedIn, remaining, rate);
    }

    public List<CheckInHistoryItem> getRecentHistory(DemoPrincipal principal, UUID eventId, int limit) {
        assertStaffAccess(principal, eventId);

        return jdbc.query(
            "SELECT c.id AS check_in_id, c.ticket_id, t.code AS ticket_code, u.display_name AS attendee_name, " +
            "tt.name AS ticket_type_name, s.label AS seat_label, c.gate_name, c.checked_in_at, staff.display_name AS staff_name " +
            "FROM ticketing.ticket_check_ins c " +
            "JOIN ticketing.tickets t ON t.id = c.ticket_id " +
            "JOIN ticketing.bookings b ON b.id = t.booking_id " +
            "JOIN ticketing.users u ON u.id = b.account_id " +
            "JOIN ticketing.booking_items bi ON bi.id = t.booking_item_id " +
            "JOIN ticketing.ticket_types tt ON tt.id = bi.ticket_type_id " +
            "LEFT JOIN ticketing.seats s ON s.id = bi.seat_id " +
            "JOIN ticketing.users staff ON staff.id = c.staff_user_id " +
            "WHERE c.event_id = ? " +
            "ORDER BY c.checked_in_at DESC " +
            "LIMIT ?",
            (rs, rowNum) -> {
                String seatLabel = rs.getString("seat_label");
                if (seatLabel == null || seatLabel.isBlank()) {
                    seatLabel = "Vé GA (Tự do)";
                }
                Timestamp checkedInAt = rs.getTimestamp("checked_in_at");
                return new CheckInHistoryItem(
                    rs.getObject("check_in_id", UUID.class),
                    rs.getObject("ticket_id", UUID.class),
                    rs.getString("ticket_code"),
                    rs.getString("attendee_name"),
                    rs.getString("ticket_type_name"),
                    seatLabel,
                    rs.getString("gate_name"),
                    checkedInAt != null ? checkedInAt.toInstant().toString() : null,
                    rs.getString("staff_name")
                );
            },
            eventId,
            limit
        );
    }

    public OfflineManifestDto getOfflineManifest(DemoPrincipal principal, UUID eventId) {
        assertStaffAccess(principal, eventId);

        var eventRows = jdbc.queryForList("SELECT title FROM ticketing.events WHERE id = ?", eventId);
        if (eventRows.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy sự kiện");
        }
        String eventTitle = (String) eventRows.getFirst().get("title");

        List<OfflineTicketItem> tickets = jdbc.query(
            "SELECT t.id AS ticket_id, t.code AS ticket_code, u.display_name AS attendee_name, " +
            "tt.name AS ticket_type_name, s.label AS seat_label, c.checked_in_at " +
            "FROM ticketing.tickets t " +
            "JOIN ticketing.bookings b ON b.id = t.booking_id " +
            "JOIN ticketing.users u ON u.id = b.account_id " +
            "JOIN ticketing.booking_items bi ON bi.id = t.booking_item_id " +
            "JOIN ticketing.ticket_types tt ON tt.id = bi.ticket_type_id " +
            "LEFT JOIN ticketing.seats s ON s.id = bi.seat_id " +
            "LEFT JOIN ticketing.ticket_check_ins c ON c.ticket_id = t.id " +
            "WHERE b.event_id = ? AND b.status = 'CONFIRMED' " +
            "ORDER BY t.created_at ASC",
            (rs, rowNum) -> {
                UUID ticketId = rs.getObject("ticket_id", UUID.class);
                String ticketCode = rs.getString("ticket_code");
                String attendeeName = rs.getString("attendee_name");
                String ticketTypeName = rs.getString("ticket_type_name");
                String seatLabel = rs.getString("seat_label");
                if (seatLabel == null || seatLabel.isBlank()) {
                    seatLabel = "Vé GA (Tự do)";
                }
                Timestamp checkedInAt = rs.getTimestamp("checked_in_at");
                boolean isCheckedIn = checkedInAt != null;
                String qrToken = ticketQrService.generateSignedQrToken(ticketId, ticketCode, eventId, Instant.now());

                return new OfflineTicketItem(
                    ticketId,
                    ticketCode,
                    attendeeName,
                    ticketTypeName,
                    seatLabel,
                    qrToken,
                    isCheckedIn,
                    isCheckedIn ? checkedInAt.toInstant().toString() : null
                );
            },
            eventId
        );

        return new OfflineManifestDto(
            eventId,
            eventTitle,
            Instant.now().toString(),
            tickets.size(),
            tickets
        );
    }

    @Transactional
    public BatchSyncResponse batchSync(DemoPrincipal principal, OfflineSyncRequest req) {
        if (req == null || req.eventId() == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thiếu thông tin sự kiện");
        }
        assertStaffAccess(principal, req.eventId());

        UUID eventId = req.eventId();
        UUID staffUserId = principal.accountId();
        java.util.List<OfflineSyncItem> items = req.items() != null ? req.items() : java.util.Collections.emptyList();

        java.util.List<OfflineSyncResultItem> results = new java.util.ArrayList<>();
        int syncedCount = 0;
        int conflictCount = 0;

        for (OfflineSyncItem item : items) {
            UUID ticketId = item.ticketId();
            String ticketCode = item.ticketCode();

            if (ticketId == null && item.qrToken() != null && !item.qrToken().isBlank()) {
                String token = item.qrToken().trim();
                if (ticketQrService.verifyQrToken(token)) {
                    String[] parts = token.split("\\.");
                    try {
                        ticketId = UUID.fromString(parts[0]);
                        ticketCode = parts[1];
                    } catch (Exception ignored) {}
                }
            }

            if (ticketId == null && ticketCode != null && !ticketCode.isBlank()) {
                var found = jdbc.queryForList(
                    "SELECT t.id FROM ticketing.tickets t " +
                    "JOIN ticketing.bookings b ON b.id = t.booking_id " +
                    "WHERE t.code = ? AND b.event_id = ?",
                    ticketCode.trim(), eventId
                );
                if (!found.isEmpty()) {
                    ticketId = (UUID) found.getFirst().get("id");
                }
            }

            if (ticketId == null) {
                results.add(new OfflineSyncResultItem(
                    item.clientSyncId(),
                    null,
                    ticketCode,
                    "INVALID_TICKET",
                    item.gateName(),
                    null,
                    "Không tìm thấy vé hợp lệ cho sự kiện này"
                ));
                continue;
            }

            var ticketCheck = jdbc.queryForList("SELECT status FROM ticketing.tickets WHERE id = ?", ticketId);
            if (!ticketCheck.isEmpty() && "REVOKED".equalsIgnoreCase((String) ticketCheck.getFirst().get("status"))) {
                results.add(new OfflineSyncResultItem(
                    item.clientSyncId(),
                    ticketId,
                    ticketCode,
                    "TICKET_REVOKED",
                    item.gateName(),
                    null,
                    "Vé đã bị thu hồi do chuyển nhượng hoặc cấp đổi mã mới"
                ));
                conflictCount++;
                continue;
            }

            Integer activeResalesCount = jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.resale_listings WHERE ticket_id = ? AND status IN ('ACTIVE', 'RESERVED')",
                Integer.class,
                ticketId
            );
            if (activeResalesCount != null && activeResalesCount > 0) {
                results.add(new OfflineSyncResultItem(
                    item.clientSyncId(),
                    ticketId,
                    ticketCode,
                    "TICKET_LOCKED_RESALE",
                    item.gateName(),
                    null,
                    "Vé đang được rao bán trên Chợ Resale, không thể đồng bộ soát vé"
                ));
                conflictCount++;
                continue;
            }

            Integer pendingTransferCount = jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.ticket_transfers WHERE ticket_id = ? AND status = 'PENDING' AND expires_at > clock_timestamp()",
                Integer.class,
                ticketId
            );
            if (pendingTransferCount != null && pendingTransferCount > 0) {
                results.add(new OfflineSyncResultItem(
                    item.clientSyncId(),
                    ticketId,
                    ticketCode,
                    "TICKET_PENDING_TRANSFER",
                    item.gateName(),
                    null,
                    "Vé đang trong tiến trình gửi tặng bạn bè, không thể đồng bộ soát vé"
                ));
                conflictCount++;
                continue;
            }

            String gate = (item.gateName() != null && !item.gateName().isBlank()) ? item.gateName().trim() : "OFFLINE_GATE";
            Timestamp clientTimestamp = null;
            if (item.clientTimestamp() != null && !item.clientTimestamp().isBlank()) {
                try {
                    clientTimestamp = Timestamp.from(Instant.parse(item.clientTimestamp()));
                } catch (Exception ignored) {
                    clientTimestamp = Timestamp.from(Instant.now());
                }
            } else {
                clientTimestamp = Timestamp.from(Instant.now());
            }

            try {
                Timestamp serverCheckInTime = jdbc.queryForObject(
                    "INSERT INTO ticketing.ticket_check_ins (id, ticket_id, event_id, staff_user_id, gate_name, device_info, checked_in_at, is_offline, synced_at, client_checked_in_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, clock_timestamp(), true, clock_timestamp(), ?) " +
                    "RETURNING checked_in_at",
                    Timestamp.class,
                    ticketId, eventId, staffUserId, gate, item.deviceInfo(), clientTimestamp
                );
                syncedCount++;
                results.add(new OfflineSyncResultItem(
                    item.clientSyncId(),
                    ticketId,
                    ticketCode,
                    "SYNCED",
                    gate,
                    serverCheckInTime != null ? serverCheckInTime.toInstant().toString() : Instant.now().toString(),
                    "Đồng bộ thành công"
                ));
            } catch (DataIntegrityViolationException ex) {
                conflictCount++;
                var prevList = jdbc.queryForList(
                    "SELECT c.checked_in_at, c.gate_name, u.display_name AS staff_name " +
                    "FROM ticketing.ticket_check_ins c " +
                    "LEFT JOIN ticketing.users u ON u.id = c.staff_user_id " +
                    "WHERE c.ticket_id = ?",
                    ticketId
                );
                String msg = "Xung đột: Vé đã được quét trước đó!";
                String checkedAtStr = null;
                if (!prevList.isEmpty()) {
                    var prev = prevList.getFirst();
                    Timestamp firstAt = (Timestamp) prev.get("checked_in_at");
                    String prevGate = (String) prev.get("gate_name");
                    String staff = (String) prev.get("staff_name");
                    checkedAtStr = firstAt != null ? firstAt.toInstant().toString() : null;
                    msg = "Xung đột: Vé đã được quét tại cổng " + prevGate + (staff != null ? " bởi " + staff : "") + (checkedAtStr != null ? " lúc " + checkedAtStr : "");
                }
                results.add(new OfflineSyncResultItem(
                    item.clientSyncId(),
                    ticketId,
                    ticketCode,
                    "ALREADY_CHECKED_IN",
                    gate,
                    checkedAtStr,
                    msg
                ));
            }
        }

        return new BatchSyncResponse(
            eventId,
            Instant.now().toString(),
            items.size(),
            syncedCount,
            conflictCount,
            results
        );
    }
}
