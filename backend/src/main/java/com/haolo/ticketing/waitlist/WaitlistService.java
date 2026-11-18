package com.haolo.ticketing.waitlist;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.haolo.ticketing.realtime.EventStreamService;
import com.haolo.ticketing.realtime.InventoryChangedEvent;
import com.haolo.ticketing.shared.ApiException;

@Service
public class WaitlistService {
    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);
    private static final int NOTIFY_BATCH_SIZE = 3;

    public record JoinWaitlistRequest(UUID ticketTypeId, Integer desiredQuantity) {}
    public record WaitlistEntry(
        UUID id,
        UUID eventId,
        String eventTitle,
        UUID ticketTypeId,
        int desiredQuantity,
        String status,
        int queuePosition,
        String notifiedAt,
        String createdAt
    ) {}

    private final JdbcTemplate jdbc;
    private final EventStreamService streamService;

    public WaitlistService(JdbcTemplate jdbc, EventStreamService streamService) {
        this.jdbc = jdbc;
        this.streamService = streamService;
    }

    @Transactional
    public WaitlistEntry join(UUID eventId, UUID userId, JoinWaitlistRequest req) {
        int qty = (req != null && req.desiredQuantity() != null) ? req.desiredQuantity() : 1;
        if (qty < 1 || qty > 6) {
            throw new ApiException(400, "VALIDATION_ERROR", "Số lượng vé mong muốn phải từ 1 đến 6");
        }
        var evRows = jdbc.queryForList("SELECT id, title FROM ticketing.events WHERE id = ? AND status = 'PUBLISHED'", eventId);
        if (evRows.isEmpty()) {
            throw ApiException.missing();
        }
        String eventTitle = (String) evRows.getFirst().get("title");
        UUID ticketTypeId = req != null ? req.ticketTypeId() : null;

        var existing = jdbc.queryForList(
            "SELECT id, ticket_type_id, desired_quantity, status, notified_at, created_at " +
                "FROM ticketing.event_waitlists WHERE event_id = ? AND user_id = ? AND status IN ('WAITING', 'NOTIFIED')",
            eventId, userId
        );
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            UUID id = (UUID) row.get("id");
            Timestamp created = (Timestamp) row.get("created_at");
            Timestamp notified = (Timestamp) row.get("notified_at");
            int pos = computePosition(eventId, created);
            return new WaitlistEntry(
                id, eventId, eventTitle, (UUID) row.get("ticket_type_id"),
                ((Number) row.get("desired_quantity")).intValue(),
                (String) row.get("status"), pos,
                notified != null ? notified.toInstant().toString() : null,
                created.toInstant().toString()
            );
        }

        UUID id = UUID.randomUUID();
        Timestamp now = jdbc.queryForObject(
            "INSERT INTO ticketing.event_waitlists(id, event_id, ticket_type_id, user_id, desired_quantity, status) " +
                "VALUES (?, ?, ?, ?, ?, 'WAITING') RETURNING created_at",
            Timestamp.class, id, eventId, ticketTypeId, userId, qty
        );
        int pos = computePosition(eventId, now);
        return new WaitlistEntry(
            id, eventId, eventTitle, ticketTypeId, qty, "WAITING", pos, null, now.toInstant().toString()
        );
    }

    public List<WaitlistEntry> listMine(UUID userId) {
        return jdbc.query(
            "SELECT w.id, w.event_id, e.title AS event_title, w.ticket_type_id, w.desired_quantity, w.status, w.notified_at, w.created_at " +
                "FROM ticketing.event_waitlists w JOIN ticketing.events e ON e.id = w.event_id " +
                "WHERE w.user_id = ? AND w.status IN ('WAITING', 'NOTIFIED') ORDER BY w.created_at DESC",
            (rs, n) -> {
                UUID eventId = rs.getObject("event_id", UUID.class);
                Timestamp created = rs.getTimestamp("created_at");
                Timestamp notified = rs.getTimestamp("notified_at");
                String status = rs.getString("status");
                int pos = "WAITING".equals(status) ? computePosition(eventId, created) : 0;
                return new WaitlistEntry(
                    rs.getObject("id", UUID.class),
                    eventId,
                    rs.getString("event_title"),
                    rs.getObject("ticket_type_id", UUID.class),
                    rs.getInt("desired_quantity"),
                    status,
                    pos,
                    notified != null ? notified.toInstant().toString() : null,
                    created.toInstant().toString()
                );
            },
            userId
        );
    }

    @Transactional
    public Map<String, Object> cancel(UUID eventId, UUID userId) {
        int updated = jdbc.update(
            "UPDATE ticketing.event_waitlists SET status = 'CANCELLED', updated_at = clock_timestamp() " +
                "WHERE event_id = ? AND user_id = ? AND status IN ('WAITING', 'NOTIFIED')",
            eventId, userId
        );
        return Map.of("eventId", eventId, "cancelled", updated > 0);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInventoryReleased(InventoryChangedEvent event) {
        if (event == null || event.eventId() == null) return;
        if ("BOOKING_EXPIRED".equals(event.reason()) || "BOOKING_CANCELLED".equals(event.reason())) {
            try {
                notifyNextWaiters(event.eventId(), event.reason());
            } catch (RuntimeException ex) {
                log.warn("Smart waitlist notification failed for event {}: {}", event.eventId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public int notifyNextWaiters(UUID eventId, String triggerReason) {
        var waiters = jdbc.queryForList(
            "SELECT w.id, w.user_id, u.email, e.title AS event_title " +
                "FROM ticketing.event_waitlists w " +
                "JOIN ticketing.users u ON u.id = w.user_id " +
                "JOIN ticketing.events e ON e.id = w.event_id " +
                "WHERE w.event_id = ? AND w.status = 'WAITING' " +
                "ORDER BY w.created_at ASC LIMIT ? FOR UPDATE OF w SKIP LOCKED",
            eventId, NOTIFY_BATCH_SIZE
        );
        int notifiedCount = 0;
        for (var row : waiters) {
            UUID waitlistId = (UUID) row.get("id");
            String email = (String) row.get("email");
            String title = (String) row.get("event_title");
            jdbc.update(
                "UPDATE ticketing.event_waitlists SET status = 'NOTIFIED', notified_at = clock_timestamp(), updated_at = clock_timestamp(), trigger_reason = ? WHERE id = ?",
                triggerReason, waitlistId
            );
            if (email != null && !email.isBlank()) {
                jdbc.update(
                    "INSERT INTO ticketing.email_notifications(id, recipient_email, subject, template_type, payload_json, status) " +
                        "VALUES (?, ?, ?, 'WAITLIST_SEAT_RELEASED', CAST(? AS jsonb), 'SENT')",
                    UUID.randomUUID(),
                    email,
                    "[EventFlow] Vé sự kiện \"" + title + "\" vừa mở lại cho bạn!",
                    "{\"eventId\":\"" + eventId + "\",\"waitlistId\":\"" + waitlistId + "\",\"trigger\":\"" + triggerReason + "\"}"
                );
            }
            notifiedCount++;
        }
        if (notifiedCount > 0 && streamService != null) {
            streamService.broadcast(new InventoryChangedEvent(eventId, "WAITLIST_NOTIFIED", null, Instant.now()));
        }
        return notifiedCount;
    }

    private int computePosition(UUID eventId, Timestamp createdAt) {
        Integer ahead = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.event_waitlists WHERE event_id = ? AND status = 'WAITING' AND created_at <= ?",
            Integer.class, eventId, createdAt
        );
        return ahead != null ? Math.max(1, ahead) : 1;
    }
}
