package com.haolo.ticketing.waitlist;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.realtime.EventStreamService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaitlistServiceTest {

    @Test
    void notifiesOldestWaitersAndLogsEmailOnSeatRelease() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EventStreamService stream = mock(EventStreamService.class);
        WaitlistService service = new WaitlistService(jdbc, stream);

        UUID eventId = UUID.randomUUID();
        UUID waitlistId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> waiterRow = new LinkedHashMap<>();
        waiterRow.put("id", waitlistId);
        waiterRow.put("user_id", userId);
        waiterRow.put("email", "customer@eventflow.vn");
        waiterRow.put("event_title", "Concert V3");

        when(jdbc.queryForList(contains("FROM ticketing.event_waitlists"), eq(eventId), anyInt()))
            .thenReturn(List.of(waiterRow));

        int notified = service.notifyNextWaiters(eventId, "BOOKING_EXPIRED");
        assertEquals(1, notified);

        verify(jdbc).update(contains("SET status = 'NOTIFIED'"), eq("BOOKING_EXPIRED"), eq(waitlistId));
        verify(jdbc).update(contains("INSERT INTO ticketing.email_notifications"), any(UUID.class), eq("customer@eventflow.vn"), contains("Concert V3"), contains(eventId.toString()));
        verify(stream).broadcast(argThat(e -> e.eventId().equals(eventId) && "WAITLIST_NOTIFIED".equals(e.reason())));
    }
}
