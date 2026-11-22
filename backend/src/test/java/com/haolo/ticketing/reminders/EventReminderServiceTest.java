package com.haolo.ticketing.reminders;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.reminders.EventReminderModels.*;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventReminderServiceTest {

    private JdbcTemplate jdbc;
    private EventReminderService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new EventReminderService(jdbc);
    }

    @Test
    void testRegisterReminder_Success() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(jdbc.queryForObject(contains("FROM ticketing.events"), eq(Integer.class), eq(eventId)))
            .thenReturn(1);

        CreateReminderResponse res = service.registerReminder(userId, eventId, 15);
        assertNotNull(res);
        assertEquals(eventId, res.eventId());
        assertEquals(15, res.remindBeforeMinutes());
        assertEquals("PENDING", res.status());
        verify(jdbc).update(contains("INSERT INTO ticketing.event_reminders"), any(), eq(userId), eq(eventId), eq(15));
    }

    @Test
    void testRegisterReminder_EventNotFound() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(jdbc.queryForObject(contains("FROM ticketing.events"), eq(Integer.class), eq(eventId)))
            .thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> service.registerReminder(userId, eventId, 15));
        assertEquals(404, ex.status);
        assertEquals("EVENT_NOT_FOUND", ex.code);
    }

    @Test
    void testGetUserReminders() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();

        EventReminderDto dto = new EventReminderDto(
            reminderId, eventId, "Concert Rock 2026", "2026-12-01T20:00:00Z", 15, "PENDING", Instant.now()
        );

        when(jdbc.query(contains("FROM ticketing.event_reminders"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of(dto));

        List<EventReminderDto> items = service.getUserReminders(userId);
        assertEquals(1, items.size());
        assertEquals("Concert Rock 2026", items.get(0).eventTitle());
    }

    @Test
    void testCancelReminder() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        service.cancelReminder(userId, eventId);
        verify(jdbc).update(contains("UPDATE ticketing.event_reminders SET status = 'CANCELLED'"), eq(userId), eq(eventId));
    }
}
