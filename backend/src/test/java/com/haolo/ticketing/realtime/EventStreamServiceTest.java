package com.haolo.ticketing.realtime;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;

class EventStreamServiceTest {

    @Test
    void subscribeAndBroadcastDeliversToActiveEmitters() {
        EventStreamService service = new EventStreamService();
        UUID eventId = UUID.randomUUID();

        SseEmitter emitter = service.subscribe(eventId);
        assertNotNull(emitter);
        assertEquals(1, service.activeSubscribers(eventId));

        int delivered = service.broadcast(new InventoryChangedEvent(eventId, "BOOKING_HELD", UUID.randomUUID(), Instant.now()));
        assertEquals(1, delivered);

        // Other event has zero subscribers
        assertEquals(0, service.broadcast(new InventoryChangedEvent(UUID.randomUUID(), "BOOKING_EXPIRED", UUID.randomUUID(), Instant.now())));
    }
}
