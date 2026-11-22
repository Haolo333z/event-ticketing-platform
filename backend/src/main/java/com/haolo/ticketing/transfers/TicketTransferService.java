package com.haolo.ticketing.transfers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.transfers.TicketTransferModels.*;

@Service
public class TicketTransferService {

    public record TicketRow(
        UUID id,
        UUID bookingId,
        UUID bookingItemId,
        int ticketOrdinal,
        String code,
        UUID seatId,
        String status,
        UUID ownerUserId
    ) {}

    public record TransferRecord(
        UUID id,
        UUID ticketId,
        UUID fromUserId,
        String toEmail,
        String status,
        Timestamp expiresAt
    ) {}

    private final JdbcTemplate jdbc;

    public TicketTransferService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public TransferItemDto initiateTransfer(DemoPrincipal sender, InitiateTransferRequest request) {
        if (sender == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        if (request == null || request.ticketId() == null || request.toEmail() == null || !request.toEmail().contains("@")) {
            throw new ApiException(400, "VALIDATION_ERROR", "Email người nhận không hợp lệ");
        }

        UUID ticketId = request.ticketId();
        String toEmail = request.toEmail().trim().toLowerCase();
        String note = request.note() != null ? request.note().trim() : "";

        // 1. Lock ticket FOR UPDATE
        var tickets = jdbc.query(
            "SELECT id, booking_id, booking_item_id, ticket_ordinal, code, seat_id, status, owner_user_id FROM ticketing.tickets WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new TicketRow(
                rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getObject("booking_item_id", UUID.class),
                rs.getInt("ticket_ordinal"),
                rs.getString("code"),
                rs.getObject("seat_id", UUID.class),
                rs.getString("status"),
                rs.getObject("owner_user_id", UUID.class)
            ),
            ticketId
        );

        if (tickets.isEmpty()) {
            throw new ApiException(404, "TICKET_NOT_FOUND", "Không tìm thấy vé yêu cầu");
        }

        TicketRow ticket = tickets.getFirst();

        // Anti-IDOR check
        if (!sender.hasRole("ADMIN") && (ticket.ownerUserId() != null && !sender.accountId().equals(ticket.ownerUserId()))) {
            throw new ApiException(403, "FORBIDDEN", "Bạn không có quyền chuyển tặng vé này");
        }

        if (!"VALID".equalsIgnoreCase(ticket.status())) {
            throw new ApiException(400, "TICKET_INVALID_STATUS", "Vé này đã bị thu hồi hoặc đã được chuyển nhượng trước đó");
        }

        // Check if ticket was already checked in
        Integer checkInCount = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.ticket_check_ins WHERE ticket_id = ?",
            Integer.class,
            ticketId
        );
        if (checkInCount != null && checkInCount > 0) {
            throw new ApiException(409, "TICKET_ALREADY_USED", "Vé này đã được soát vé tại cổng sự kiện, không thể chuyển nhượng");
        }

        // Check if ticket is listed on resale marketplace
        Integer activeResales = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.resale_listings WHERE ticket_id = ? AND status IN ('ACTIVE', 'RESERVED')",
            Integer.class,
            ticketId
        );
        if (activeResales != null && activeResales > 0) {
            throw new ApiException(409, "TICKET_LISTED_ON_RESALE", "Vé đang được rao bán trên Chợ Resale, không thể gửi tặng!");
        }

        // Check if active transfer already exists
        Integer pendingTransfers = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.ticket_transfers WHERE ticket_id = ? AND status = 'PENDING' AND expires_at > clock_timestamp()",
            Integer.class,
            ticketId
        );
        if (pendingTransfers != null && pendingTransfers > 0) {
            throw new ApiException(409, "TRANSFER_ALREADY_PENDING", "Vé này đang có một giao dịch chuyển tặng chưa kết thúc");
        }

        UUID transferId = UUID.randomUUID();
        String transferToken = "TRF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        jdbc.update(
            """
            INSERT INTO ticketing.ticket_transfers (id, ticket_id, from_user_id, to_email, transfer_token, note, status, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING', clock_timestamp() + INTERVAL '48 hours', clock_timestamp())
            """,
            transferId, ticketId, sender.accountId(), toEmail, transferToken, note
        );

        // Fetch event title for display
        String eventTitle = "Sự kiện EventFlow";
        try {
            eventTitle = jdbc.queryForObject(
                "SELECT e.title FROM ticketing.tickets t JOIN ticketing.bookings b ON t.booking_id = b.id JOIN ticketing.events e ON b.event_id = e.id WHERE t.id = ?",
                String.class,
                ticketId
            );
        } catch (Exception ignored) {}

        Instant expiresAt = Instant.now().plusSeconds(48 * 3600);
        return new TransferItemDto(
            transferId, ticketId, ticket.code(), eventTitle, toEmail, "PENDING", transferToken, note, expiresAt, Instant.now()
        );
    }

    @Transactional
    public AcceptTransferResponse acceptTransfer(DemoPrincipal recipient, String transferToken) {
        if (recipient == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập để nhận vé");
        }
        if (transferToken == null || transferToken.isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã liên kết nhận vé không hợp lệ");
        }

        // 1. Lock transfer FOR UPDATE
        var transfers = jdbc.query(
            "SELECT id, ticket_id, from_user_id, to_email, status, expires_at FROM ticketing.ticket_transfers WHERE transfer_token = ? FOR UPDATE",
            (rs, rowNum) -> new TransferRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getObject("from_user_id", UUID.class),
                rs.getString("to_email"),
                rs.getString("status"),
                rs.getTimestamp("expires_at")
            ),
            transferToken.trim()
        );

        if (transfers.isEmpty()) {
            throw new ApiException(404, "TRANSFER_NOT_FOUND", "Không tìm thấy giao dịch chuyển vé hoặc liên kết không đúng");
        }

        TransferRecord transfer = transfers.getFirst();

        if (!"PENDING".equalsIgnoreCase(transfer.status())) {
            throw new ApiException(400, "TRANSFER_NOT_PENDING", "Giao dịch chuyển vé này đã hoàn tất hoặc đã bị hủy");
        }

        if (transfer.expiresAt() != null && transfer.expiresAt().toInstant().isBefore(Instant.now())) {
            jdbc.update("UPDATE ticketing.ticket_transfers SET status = 'EXPIRED' WHERE id = ?", transfer.id());
            throw new ApiException(400, "TRANSFER_EXPIRED", "Liên kết tặng vé này đã hết hạn hiệu lực (quá 48 giờ)");
        }

        if (transfer.fromUserId().equals(recipient.accountId())) {
            throw new ApiException(400, "CANNOT_TRANSFER_TO_SELF", "Bạn không thể tự nhận vé do chính mình gửi tặng");
        }

        // 2. Lock old ticket FOR UPDATE
        var oldTickets = jdbc.query(
            "SELECT id, booking_id, booking_item_id, ticket_ordinal, code, seat_id, status, owner_user_id FROM ticketing.tickets WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new TicketRow(
                rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getObject("booking_item_id", UUID.class),
                rs.getInt("ticket_ordinal"),
                rs.getString("code"),
                rs.getObject("seat_id", UUID.class),
                rs.getString("status"),
                rs.getObject("owner_user_id", UUID.class)
            ),
            transfer.ticketId()
        );

        if (oldTickets.isEmpty()) {
            throw new ApiException(404, "TICKET_NOT_FOUND", "Vé gốc không tồn tại");
        }

        TicketRow oldTicket = oldTickets.getFirst();
        if (!"VALID".equalsIgnoreCase(oldTicket.status())) {
            throw new ApiException(400, "TICKET_ALREADY_REVOKED", "Vé gốc đã bị thu hồi hoặc không còn hiệu lực");
        }

        // 3. Atomically REVOKE old ticket
        jdbc.update("UPDATE ticketing.tickets SET status = 'REVOKED' WHERE id = ?", oldTicket.id());

        // 4. Atomically ISSUE new ticket to recipient
        UUID newTicketId = UUID.randomUUID();
        String newCode = "EF-TRF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        jdbc.update(
            """
            INSERT INTO ticketing.tickets (id, booking_id, booking_item_id, ticket_ordinal, code, seat_id, status, owner_user_id, transferred_from_ticket_id, transferred_at)
            VALUES (?, ?, ?, ?, ?, ?, 'VALID', ?, ?, clock_timestamp())
            """,
            newTicketId,
            oldTicket.bookingId(),
            oldTicket.bookingItemId(),
            oldTicket.ticketOrdinal(),
            newCode,
            oldTicket.seatId(),
            recipient.accountId(),
            oldTicket.id()
        );

        // 5. Update transfer record
        jdbc.update(
            "UPDATE ticketing.ticket_transfers SET status = 'ACCEPTED', to_user_id = ?, accepted_at = clock_timestamp(), new_ticket_id = ? WHERE id = ?",
            recipient.accountId(), newTicketId, transfer.id()
        );

        // 6. Write Transactional Outbox Event for Kafka
        UUID outboxId = UUID.randomUUID();
        String payloadJson = String.format(
            "{\"transferId\":\"%s\",\"oldTicketId\":\"%s\",\"newTicketId\":\"%s\",\"fromUserId\":\"%s\",\"toUserId\":\"%s\"}",
            transfer.id(), oldTicket.id(), newTicketId, transfer.fromUserId(), recipient.accountId()
        );
        jdbc.update(
            """
            INSERT INTO ticketing.outbox_events (id, event_type, aggregate_id, correlation_id, schema_version, payload)
            VALUES (?, 'TICKET_TRANSFERRED', ?, ?, 1, ?::jsonb)
            """,
            outboxId, newTicketId, transfer.id(), payloadJson
        );

        return new AcceptTransferResponse(
            transfer.id(),
            oldTicket.id(),
            newTicketId,
            newCode,
            "ACCEPTED",
            "Nhận vé tặng thành công! Mã QR mới đã được kích hoạt trong ví vé của bạn."
        );
    }

    @Transactional
    public void cancelTransfer(DemoPrincipal sender, UUID transferId) {
        if (sender == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }

        var rows = jdbc.query(
            "SELECT id, from_user_id, status FROM ticketing.ticket_transfers WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new Object[] { rs.getObject("id", UUID.class), rs.getObject("from_user_id", UUID.class), rs.getString("status") },
            transferId
        );

        if (rows.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy giao dịch chuyển vé");
        }

        Object[] row = rows.getFirst();
        UUID fromUserId = (UUID) row[1];
        String status = (String) row[2];

        if (!sender.hasRole("ADMIN") && !sender.accountId().equals(fromUserId)) {
            throw new ApiException(403, "FORBIDDEN", "Bạn không phải người tạo giao dịch này");
        }

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new ApiException(400, "CANNOT_CANCEL", "Chỉ có thể hủy giao dịch đang ở trạng thái chờ nhận");
        }

        jdbc.update("UPDATE ticketing.ticket_transfers SET status = 'CANCELLED' WHERE id = ?", transferId);
    }

    public TransferDetailDto getTransferByToken(String transferToken) {
        if (transferToken == null || transferToken.isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Token không hợp lệ");
        }

        String sql = """
            SELECT tr.id, tr.ticket_id, t.code AS ticket_code,
                   e.title AS event_title, e.starts_at, s.label AS seat_label,
                   u_from.email AS from_email, u_from.display_name AS from_name,
                   tr.to_email, tr.note, tr.status, tr.transfer_token, tr.expires_at, tr.created_at
            FROM ticketing.ticket_transfers tr
            JOIN ticketing.tickets t ON tr.ticket_id = t.id
            JOIN ticketing.bookings b ON t.booking_id = b.id
            JOIN ticketing.events e ON b.event_id = e.id
            JOIN ticketing.users u_from ON tr.from_user_id = u_from.id
            LEFT JOIN ticketing.seats s ON t.seat_id = s.id
            WHERE tr.transfer_token = ?
            """;

        List<TransferDetailDto> list = jdbc.query(sql, (rs, rowNum) -> {
            Timestamp exp = rs.getTimestamp("expires_at");
            Timestamp crt = rs.getTimestamp("created_at");
            return new TransferDetailDto(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getString("ticket_code"),
                rs.getString("event_title"),
                rs.getString("starts_at"),
                rs.getString("seat_label"),
                rs.getString("from_email"),
                rs.getString("from_name"),
                rs.getString("to_email"),
                rs.getString("note"),
                rs.getString("status"),
                rs.getString("transfer_token"),
                exp != null ? exp.toInstant() : Instant.now(),
                crt != null ? crt.toInstant() : Instant.now()
            );
        }, transferToken.trim());

        if (list.isEmpty()) {
            throw new ApiException(404, "TRANSFER_NOT_FOUND", "Không tìm thấy giao dịch tặng vé");
        }

        return list.getFirst();
    }

    public List<TransferItemDto> getSentTransfers(UUID userId) {
        if (userId == null) return Collections.emptyList();
        String sql = """
            SELECT tr.id, tr.ticket_id, t.code AS ticket_code, e.title AS event_title,
                   tr.to_email, tr.status, tr.transfer_token, tr.note, tr.expires_at, tr.created_at
            FROM ticketing.ticket_transfers tr
            JOIN ticketing.tickets t ON tr.ticket_id = t.id
            JOIN ticketing.bookings b ON t.booking_id = b.id
            JOIN ticketing.events e ON b.event_id = e.id
            WHERE tr.from_user_id = ?
            ORDER BY tr.created_at DESC
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            Timestamp exp = rs.getTimestamp("expires_at");
            Timestamp crt = rs.getTimestamp("created_at");
            return new TransferItemDto(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getString("ticket_code"),
                rs.getString("event_title"),
                rs.getString("to_email"),
                rs.getString("status"),
                rs.getString("transfer_token"),
                rs.getString("note"),
                exp != null ? exp.toInstant() : Instant.now(),
                crt != null ? crt.toInstant() : Instant.now()
            );
        }, userId);
    }

    public List<TransferItemDto> getReceivedTransfers(UUID userId) {
        if (userId == null) return Collections.emptyList();
        String sql = """
            SELECT tr.id, tr.ticket_id, COALESCE(new_t.code, t.code) AS ticket_code, e.title AS event_title,
                   u_from.email AS from_email, tr.status, tr.transfer_token, tr.note, tr.expires_at, tr.created_at
            FROM ticketing.ticket_transfers tr
            JOIN ticketing.tickets t ON tr.ticket_id = t.id
            JOIN ticketing.bookings b ON t.booking_id = b.id
            JOIN ticketing.events e ON b.event_id = e.id
            JOIN ticketing.users u_from ON tr.from_user_id = u_from.id
            LEFT JOIN ticketing.tickets new_t ON tr.new_ticket_id = new_t.id
            WHERE tr.to_user_id = ?
            ORDER BY tr.created_at DESC
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            Timestamp exp = rs.getTimestamp("expires_at");
            Timestamp crt = rs.getTimestamp("created_at");
            return new TransferItemDto(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getString("ticket_code"),
                rs.getString("event_title"),
                rs.getString("from_email"),
                rs.getString("status"),
                rs.getString("transfer_token"),
                rs.getString("note"),
                exp != null ? exp.toInstant() : Instant.now(),
                crt != null ? crt.toInstant() : Instant.now()
            );
        }, userId);
    }
}
