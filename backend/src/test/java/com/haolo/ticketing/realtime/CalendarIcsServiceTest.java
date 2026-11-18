package com.haolo.ticketing.realtime;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CalendarIcsServiceTest {

    @Test
    void generatesValidRfc5545IcsForOnlineEvent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID eventId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", eventId);
        row.put("title", "AI Summit, Vietnam 2026");
        row.put("description", "Line 1\nLine 2");
        row.put("venue", "Online Stream");
        row.put("starts_at", Timestamp.from(Instant.parse("2026-12-20T12:00:00Z")));
        row.put("event_format", "ONLINE");
        row.put("online_join_url", "https://meet.eventflow.vn/ai-summit");

        when(jdbc.queryForList(anyString(), eq(eventId))).thenReturn(List.of(row));

        CalendarIcsService service = new CalendarIcsService(jdbc);
        String ics = service.generateEventIcs(eventId);

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("SUMMARY:AI Summit\\, Vietnam 2026"));
        assertTrue(ics.contains("DTSTART:20261220T120000Z"));
        assertTrue(ics.contains("DTEND:20261220T140000Z"));
        assertTrue(ics.contains("URL:https://meet.eventflow.vn/ai-summit"));
        assertTrue(ics.contains("END:VCALENDAR"));
    }
}
