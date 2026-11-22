package com.haolo.ticketing.resale;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.realtime.InventoryChangedEvent;
import com.haolo.ticketing.resale.ResaleListingModels.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class ResaleListingService {

    public record TicketDetailRow(
        UUID id,
        UUID bookingId,
        UUID bookingItemId,
        int ticketOrdinal,
        String code,
        UUID seatId,
        String status,
        UUID ownerUserId,
        UUID bookingAccountId,
        UUID eventId,
        long unitPriceVnd,
        String eventTitle,
        String ticketTypeTitle,
        String seatLabel
    ) {}

    public record ListingRow(
        UUID id,
        UUID ticketId,
        UUID sellerUserId,
        UUID eventId,
        long originalPriceVnd,
        long askingPriceVnd,
        String status,
        UUID lockedByUserId,
        Timestamp lockExpiresAt,
        UUID buyerUserId,
        Timestamp createdAt,
        Timestamp soldAt
    ) {}

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    @Autowired
    public ResaleListingService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public ResaleListingService(JdbcTemplate jdbc) {
        this(jdbc, null);
    }

    @Transactional
    public ResaleListingDto createListing(DemoPrincipal seller, CreateResaleListingRequest req) {
        if (seller == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        if (req == null || req.ticketId() == null || req.askingPriceVnd() == null || req.askingPriceVnd() <= 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thông tin niêm yết bán lại không hợp lệ");
        }

        UUID ticketId = req.ticketId();
        long askingPrice = req.askingPriceVnd();

        // 1. Lock ticket and verify ownership and original price
        var tickets = jdbc.query(
            """
            SELECT t.id, t.booking_id, t.booking_item_id, t.ticket_ordinal, t.code, t.seat_id, t.status, t.owner_user_id,
                   b.account_id AS booking_account_id, b.event_id,
                   bi.unit_price_vnd,
                   e.title AS event_title,
                   tt.name AS ticket_type_title,
                   s.label AS seat_label
            FROM ticketing.tickets t
            JOIN ticketing.bookings b ON t.booking_id = b.id
            JOIN ticketing.booking_items bi ON t.booking_item_id = bi.id
            JOIN ticketing.ticket_types tt ON bi.ticket_type_id = tt.id
            JOIN ticketing.events e ON b.event_id = e.id
            LEFT JOIN ticketing.seats s ON t.seat_id = s.id
            WHERE t.id = ?
            FOR UPDATE
            """,
            (rs, rowNum) -> new TicketDetailRow(
                rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getObject("booking_item_id", UUID.class),
                rs.getInt("ticket_ordinal"),
                rs.getString("code"),
                rs.getObject("seat_id", UUID.class),
                rs.getString("status"),
                rs.getObject("owner_user_id", UUID.class),
                rs.getObject("booking_account_id", UUID.class),
                rs.getObject("eventId", UUID.class),
                rs.getLong("unit_price_vnd"),
                rs.getString("event_title"),
                rs.getString("ticket_type_title"),
                rs.getString("seat_label")
            ),
            ticketId
        );

        if (tickets.isEmpty()) {
            throw new ApiException(404, "TICKET_NOT_FOUND", "Không tìm thấy vé yêu cầu");
        }

        TicketDetailRow ticket = tickets.getFirst();

        // Anti-IDOR check
        UUID currentOwner = ticket.ownerUserId() != null ? ticket.ownerUserId() : ticket.bookingAccountId();
        if (!seller.hasRole("ADMIN") && !seller.accountId().equals(currentOwner)) {
            throw new ApiException(403, "FORBIDDEN", "Bạn không sở hữu vé này");
        }

        if (!"VALID".equalsIgnoreCase(ticket.status())) {
            throw new ApiException(400, "TICKET_INVALID_STATUS", "Vé không hợp lệ hoặc đã bị thu hồi/chuyển nhượng");
        }

        // Check if ticket already checked in at event
        Integer checkInCount = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.ticket_check_ins WHERE ticket_id = ?",
            Integer.class,
            ticketId
        );
        if (checkInCount != null && checkInCount > 0) {
            throw new ApiException(409, "TICKET_ALREADY_USED", "Vé này đã được soát vé tại cổng sự kiện, không thể bán lại");
        }

        // Check if active transfer is pending
        Integer pendingTransfers = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.ticket_transfers WHERE ticket_id = ? AND status = 'PENDING' AND expires_at > clock_timestamp()",
            Integer.class,
            ticketId
        );
        if (pendingTransfers != null && pendingTransfers > 0) {
            throw new ApiException(409, "TRANSFER_ALREADY_PENDING", "Vé này đang trong tiến trình gửi tặng, không thể đăng bán");
        }

        // 2. Anti-Scalping Invariant: asking price must not exceed original price
        if (askingPrice > ticket.unitPriceVnd()) {
            throw new ApiException(400, "RESALE_PRICE_EXCEEDS_ORIGINAL",
                "Giá bán lại (" + askingPrice + " đ) không được vượt quá giá gốc của vé (" + ticket.unitPriceVnd() + " đ)");
        }

        // 3. Check if active listing already exists
        Integer activeListings = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.resale_listings WHERE ticket_id = ? AND status IN ('LISTED', 'RESERVED')",
            Integer.class,
            ticketId
        );
        if (activeListings != null && activeListings > 0) {
            throw new ApiException(409, "RESALE_ALREADY_LISTED", "Vé này đã được niêm yết bán trên hệ thống");
        }

        UUID listingId = UUID.randomUUID();
        jdbc.update(
            """
            INSERT INTO ticketing.resale_listings (id, ticket_id, seller_user_id, event_id, original_price_vnd, asking_price_vnd, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'LISTED', clock_timestamp())
            """,
            listingId, ticketId, seller.accountId(), ticket.eventId(), ticket.unitPriceVnd(), askingPrice
        );

        String sellerName = "Khán giả " + seller.accountId().toString().substring(0, 8);
        try {
            var name = jdbc.queryForObject("SELECT display_name FROM ticketing.users WHERE id = ?", String.class, seller.accountId());
            if (name != null && !name.isBlank()) {
                sellerName = name;
            }
        } catch (Exception ignored) {}

        return new ResaleListingDto(
            listingId,
            ticketId,
            ticket.code(),
            ticket.eventId(),
            ticket.eventTitle(),
            ticket.ticketTypeTitle(),
            ticket.seatLabel(),
            seller.accountId(),
            sellerName,
            ticket.unitPriceVnd(),
            askingPrice,
            "LISTED",
            null,
            null,
            null,
            Instant.now(),
            null
        );
    }

    public List<ResaleListingDto> listActiveListings(UUID eventId) {
        if (eventId == null) return List.of();

        return jdbc.query(
            """
            SELECT rl.id, rl.ticket_id, rl.seller_user_id, rl.event_id, rl.original_price_vnd, rl.asking_price_vnd,
                   rl.status, rl.locked_by_user_id, rl.lock_expires_at, rl.buyer_user_id, rl.created_at, rl.sold_at,
                   t.code AS ticket_code,
                   e.title AS event_title,
                   tt.name AS ticket_type_title,
                   s.label AS seat_label,
                   u.display_name AS seller_name
            FROM ticketing.resale_listings rl
            JOIN ticketing.tickets t ON rl.ticket_id = t.id
            JOIN ticketing.booking_items bi ON t.booking_item_id = bi.id
            JOIN ticketing.ticket_types tt ON bi.ticket_type_id = tt.id
            JOIN ticketing.events e ON rl.event_id = e.id
            JOIN ticketing.users u ON rl.seller_user_id = u.id
            LEFT JOIN ticketing.seats s ON t.seat_id = s.id
            WHERE rl.event_id = ?
              AND (rl.status = 'LISTED' OR (rl.status = 'RESERVED' AND rl.lock_expires_at < clock_timestamp()))
            ORDER BY rl.created_at DESC
            """,
            (rs, rowNum) -> new ResaleListingDto(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getString("ticket_code"),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_title"),
                rs.getString("ticket_type_title"),
                rs.getString("seat_label"),
                rs.getObject("seller_user_id", UUID.class),
                rs.getString("seller_name"),
                rs.getLong("original_price_vnd"),
                rs.getLong("asking_price_vnd"),
                "LISTED",
                null,
                null,
                null,
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                null
            ),
            eventId
        );
    }

    @Transactional
    public ReserveResaleResponse reserveListing(DemoPrincipal buyer, UUID listingId) {
        if (buyer == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        if (listingId == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã niêm yết không hợp lệ");
        }

        // Lock listing row
        var listings = jdbc.query(
            "SELECT id, ticket_id, seller_user_id, event_id, original_price_vnd, asking_price_vnd, status, locked_by_user_id, lock_expires_at, buyer_user_id, created_at, sold_at FROM ticketing.resale_listings WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new ListingRow(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getObject("seller_user_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getLong("original_price_vnd"),
                rs.getLong("asking_price_vnd"),
                rs.getString("status"),
                rs.getObject("locked_by_user_id", UUID.class),
                rs.getTimestamp("lock_expires_at"),
                rs.getObject("buyer_user_id", UUID.class),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("sold_at")
            ),
            listingId
        );

        if (listings.isEmpty()) {
            throw new ApiException(404, "LISTING_NOT_FOUND", "Không tìm thấy thông tin niêm yết vé bán lại");
        }

        ListingRow listing = listings.getFirst();

        if (listing.sellerUserId().equals(buyer.accountId())) {
            throw new ApiException(400, "CANNOT_BUY_OWN_TICKET", "Bạn không thể mua lại vé do chính mình niêm yết");
        }

        boolean isAvailable = "LISTED".equalsIgnoreCase(listing.status()) ||
            ("RESERVED".equalsIgnoreCase(listing.status()) && listing.lockExpiresAt() != null && listing.lockExpiresAt().toInstant().isBefore(Instant.now()));

        if (!isAvailable) {
            throw new ApiException(409, "RESALE_UNAVAILABLE", "Vé này hiện đang được giữ chỗ hoặc đã được người khác mua");
        }

        Instant lockExpiresAt = Instant.now().plusSeconds(600); // 10 minutes hold
        jdbc.update(
            """
            UPDATE ticketing.resale_listings
            SET status = 'RESERVED',
                locked_by_user_id = ?,
                lock_expires_at = ?
            WHERE id = ?
            """,
            buyer.accountId(),
            Timestamp.from(lockExpiresAt),
            listingId
        );

        if (events != null) {
            events.publishEvent(new InventoryChangedEvent(listing.eventId(), "RESALE_RESERVED", null, Instant.now()));
        }

        return new ReserveResaleResponse(
            listingId,
            lockExpiresAt,
            "RESERVED",
            "Đã giữ chỗ vé bán lại thành công trong 10 phút. Vui lòng hoàn tất thanh toán."
        );
    }

    @Transactional
    public SettleResaleResponse settlePurchase(DemoPrincipal buyer, UUID listingId) {
        if (buyer == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        if (listingId == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã niêm yết không hợp lệ");
        }

        // Lock listing row
        var listings = jdbc.query(
            "SELECT id, ticket_id, seller_user_id, event_id, original_price_vnd, asking_price_vnd, status, locked_by_user_id, lock_expires_at, buyer_user_id, created_at, sold_at FROM ticketing.resale_listings WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new ListingRow(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getObject("seller_user_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getLong("original_price_vnd"),
                rs.getLong("asking_price_vnd"),
                rs.getString("status"),
                rs.getObject("locked_by_user_id", UUID.class),
                rs.getTimestamp("lock_expires_at"),
                rs.getObject("buyer_user_id", UUID.class),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("sold_at")
            ),
            listingId
        );

        if (listings.isEmpty()) {
            throw new ApiException(404, "LISTING_NOT_FOUND", "Không tìm thấy thông tin niêm yết vé bán lại");
        }

        ListingRow listing = listings.getFirst();

        if (!"RESERVED".equalsIgnoreCase(listing.status()) || listing.lockedByUserId() == null || !listing.lockedByUserId().equals(buyer.accountId())) {
            throw new ApiException(400, "INVALID_RESERVATION", "Bạn không có quyền thanh toán cho phiên giữ chỗ này");
        }

        if (listing.lockExpiresAt() != null && listing.lockExpiresAt().toInstant().isBefore(Instant.now())) {
            throw new ApiException(400, "RESERVATION_EXPIRED", "Thời gian giữ chỗ thanh toán 10 phút đã hết hạn");
        }

        // Lock and revoke old ticket
        var tickets = jdbc.query(
            "SELECT id, booking_id, booking_item_id, ticket_ordinal, code, seat_id, status, owner_user_id FROM ticketing.tickets WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new TicketDetailRow(
                rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getObject("booking_item_id", UUID.class),
                rs.getInt("ticket_ordinal"),
                rs.getString("code"),
                rs.getObject("seat_id", UUID.class),
                rs.getString("status"),
                rs.getObject("owner_user_id", UUID.class),
                null, null, 0, null, null, null
            ),
            listing.ticketId()
        );

        if (tickets.isEmpty()) {
            throw new ApiException(404, "TICKET_NOT_FOUND", "Vé gốc không tồn tại");
        }

        TicketDetailRow oldTicket = tickets.getFirst();
        if (!"VALID".equalsIgnoreCase(oldTicket.status())) {
            throw new ApiException(400, "TICKET_ALREADY_REVOKED", "Vé gốc đã bị thu hồi hoặc đã sang nhượng");
        }

        // 1. Atomically REVOKE old ticket
        jdbc.update("UPDATE ticketing.tickets SET status = 'REVOKED' WHERE id = ?", oldTicket.id());

        // 2. Atomically ISSUE new ticket to buyer
        UUID newTicketId = UUID.randomUUID();
        String newCode = "EF-RSL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

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
            buyer.accountId(),
            oldTicket.id()
        );

        // 3. Mark listing SOLD
        jdbc.update(
            """
            UPDATE ticketing.resale_listings
            SET status = 'SOLD',
                buyer_user_id = ?,
                sold_at = clock_timestamp()
            WHERE id = ?
            """,
            buyer.accountId(),
            listingId
        );

        // 4. Write Transactional Outbox Event for Kafka
        UUID outboxId = UUID.randomUUID();
        String payloadJson = String.format(
            "{\"listingId\":\"%s\",\"oldTicketId\":\"%s\",\"newTicketId\":\"%s\",\"sellerUserId\":\"%s\",\"buyerUserId\":\"%s\",\"askingPriceVnd\":%d}",
            listingId, oldTicket.id(), newTicketId, listing.sellerUserId(), buyer.accountId(), listing.askingPriceVnd()
        );
        jdbc.update(
            """
            INSERT INTO ticketing.outbox_events (id, event_type, aggregate_id, correlation_id, schema_version, payload)
            VALUES (?, 'TICKET_RESOLD', ?, ?, 1, ?::jsonb)
            """,
            outboxId, newTicketId, listingId, payloadJson
        );

        if (events != null) {
            events.publishEvent(new InventoryChangedEvent(listing.eventId(), "TICKET_RESOLD", null, Instant.now()));
        }

        return new SettleResaleResponse(
            listingId,
            newTicketId,
            newCode,
            "SOLD",
            "Mua vé sang nhượng chính chủ thành công! Vé mới đã được cấp vào ví của bạn."
        );
    }

    @Transactional
    public Map<String, Object> cancelListing(DemoPrincipal seller, UUID listingId) {
        if (seller == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        if (listingId == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã niêm yết không hợp lệ");
        }

        var listings = jdbc.query(
            "SELECT id, ticket_id, seller_user_id, event_id, original_price_vnd, asking_price_vnd, status, locked_by_user_id, lock_expires_at, buyer_user_id, created_at, sold_at FROM ticketing.resale_listings WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new ListingRow(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getObject("seller_user_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getLong("original_price_vnd"),
                rs.getLong("asking_price_vnd"),
                rs.getString("status"),
                rs.getObject("locked_by_user_id", UUID.class),
                rs.getTimestamp("lock_expires_at"),
                rs.getObject("buyer_user_id", UUID.class),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("sold_at")
            ),
            listingId
        );

        if (listings.isEmpty()) {
            throw new ApiException(404, "LISTING_NOT_FOUND", "Không tìm thấy niêm yết");
        }

        ListingRow listing = listings.getFirst();
        if (!seller.hasRole("ADMIN") && !listing.sellerUserId().equals(seller.accountId())) {
            throw new ApiException(403, "FORBIDDEN", "Bạn không có quyền hủy niêm yết này");
        }

        if ("SOLD".equalsIgnoreCase(listing.status())) {
            throw new ApiException(400, "CANNOT_CANCEL_SOLD", "Vé đã được bán, không thể hủy");
        }

        if ("RESERVED".equalsIgnoreCase(listing.status()) && listing.lockExpiresAt() != null && listing.lockExpiresAt().toInstant().isAfter(Instant.now())) {
            throw new ApiException(409, "CANNOT_CANCEL_RESERVED", "Vé đang trong thời gian khách hàng giữ chỗ thanh toán, không thể hủy");
        }

        jdbc.update("UPDATE ticketing.resale_listings SET status = 'CANCELLED' WHERE id = ?", listingId);

        if (events != null) {
            events.publishEvent(new InventoryChangedEvent(listing.eventId(), "RESALE_CANCELLED", null, Instant.now()));
        }

        return Map.of("success", true, "message", "Đã hủy niêm yết vé bán lại thành công.");
    }

    public List<ResaleListingDto> getMyListings(DemoPrincipal seller) {
        if (seller == null) return List.of();

        return jdbc.query(
            """
            SELECT rl.id, rl.ticket_id, rl.seller_user_id, rl.event_id, rl.original_price_vnd, rl.asking_price_vnd,
                   rl.status, rl.locked_by_user_id, rl.lock_expires_at, rl.buyer_user_id, rl.created_at, rl.sold_at,
                   t.code AS ticket_code,
                   e.title AS event_title,
                   tt.name AS ticket_type_title,
                   s.label AS seat_label,
                   u.display_name AS seller_name
            FROM ticketing.resale_listings rl
            JOIN ticketing.tickets t ON rl.ticket_id = t.id
            JOIN ticketing.booking_items bi ON t.booking_item_id = bi.id
            JOIN ticketing.ticket_types tt ON bi.ticket_type_id = tt.id
            JOIN ticketing.events e ON rl.event_id = e.id
            JOIN ticketing.users u ON rl.seller_user_id = u.id
            LEFT JOIN ticketing.seats s ON t.seat_id = s.id
            WHERE rl.seller_user_id = ?
            ORDER BY rl.created_at DESC
            """,
            (rs, rowNum) -> new ResaleListingDto(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getString("ticket_code"),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_title"),
                rs.getString("ticket_type_title"),
                rs.getString("seat_label"),
                rs.getObject("seller_user_id", UUID.class),
                rs.getString("seller_name"),
                rs.getLong("original_price_vnd"),
                rs.getLong("asking_price_vnd"),
                rs.getString("status"),
                rs.getObject("locked_by_user_id", UUID.class),
                rs.getTimestamp("lock_expires_at") != null ? rs.getTimestamp("lock_expires_at").toInstant() : null,
                rs.getObject("buyer_user_id", UUID.class),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("sold_at") != null ? rs.getTimestamp("sold_at").toInstant() : null
            ),
            seller.accountId()
        );
    }
}
