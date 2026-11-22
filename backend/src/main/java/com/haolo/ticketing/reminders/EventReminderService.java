package com.haolo.ticketing.reminders;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.reminders.EventReminderModels.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class EventReminderService {

    private final JdbcTemplate jdbc;

    public EventReminderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public CreateReminderResponse registerReminder(UUID userId, UUID eventId, Integer remindBeforeMinutes) {
        if (userId == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập để đăng ký nhận thông báo mở bán");
        }
        if (eventId == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã sự kiện không hợp lệ");
        }

        int minutes = (remindBeforeMinutes != null && remindBeforeMinutes > 0) ? remindBeforeMinutes : 15;

        // Verify event exists
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.events WHERE id = ?",
            Integer.class,
            eventId
        );
        if (count == null || count == 0) {
            throw new ApiException(404, "EVENT_NOT_FOUND", "Không tìm thấy sự kiện");
        }

        UUID reminderId = UUID.randomUUID();
        jdbc.update(
            """
            INSERT INTO ticketing.event_reminders (id, user_id, event_id, remind_before_minutes, status, created_at)
            VALUES (?, ?, ?, ?, 'PENDING', clock_timestamp())
            ON CONFLICT (user_id, event_id)
            DO UPDATE SET remind_before_minutes = EXCLUDED.remind_before_minutes, status = 'PENDING'
            """,
            reminderId, userId, eventId, minutes
        );

        return new CreateReminderResponse(
            reminderId,
            eventId,
            minutes,
            "PENDING",
            "Đã cài đặt nhắc nhở trước " + minutes + " phút trước giờ mở bán thành công"
        );
    }

    public List<EventReminderDto> getUserReminders(UUID userId) {
        if (userId == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }

        String sql = """
            SELECT r.id, r.event_id, e.title, e.starts_at, r.remind_before_minutes, r.status, r.created_at
            FROM ticketing.event_reminders r
            JOIN ticketing.events e ON r.event_id = e.id
            WHERE r.user_id = ?
            ORDER BY r.created_at DESC
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("created_at");
            Instant createdAt = ts != null ? ts.toInstant() : Instant.now();
            return new EventReminderDto(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("title"),
                rs.getString("starts_at"),
                rs.getInt("remind_before_minutes"),
                rs.getString("status"),
                createdAt
            );
        }, userId);
    }

    @Transactional
    public void cancelReminder(UUID userId, UUID eventId) {
        if (userId == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        jdbc.update(
            "UPDATE ticketing.event_reminders SET status = 'CANCELLED' WHERE user_id = ? AND event_id = ?",
            userId, eventId
        );
    }
}
