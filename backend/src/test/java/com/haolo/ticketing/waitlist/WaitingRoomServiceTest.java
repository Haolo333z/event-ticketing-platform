package com.haolo.ticketing.waitlist;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.haolo.ticketing.waitlist.WaitingRoomService.WaitingRoomStatus;
import static org.junit.jupiter.api.Assertions.*;

class WaitingRoomServiceTest {

    @Test
    void admitsUpToCapacityThenQueuesFifoAndPromotesOnRelease() {
        WaitingRoomService service = new WaitingRoomService(2, 120);
        UUID eventId = UUID.randomUUID();

        WaitingRoomStatus s1 = service.enter(eventId, "u1");
        assertEquals("ADMITTED", s1.status());
        assertNotNull(s1.admissionToken());

        WaitingRoomStatus s2 = service.enter(eventId, "u2");
        assertEquals("ADMITTED", s2.status());

        WaitingRoomStatus s3 = service.enter(eventId, "u3");
        assertEquals("QUEUED", s3.status());
        assertEquals(1, s3.position());
        assertTrue(s3.estimatedWaitSeconds() >= 5);

        WaitingRoomStatus s4 = service.enter(eventId, "u4");
        assertEquals("QUEUED", s4.status());
        assertEquals(2, s4.position());

        // Release u1 -> u3 should automatically be promoted to ADMITTED
        service.releaseSlot(eventId, "u1");
        WaitingRoomStatus s3After = service.status(eventId, "u3");
        assertEquals("ADMITTED", s3After.status());
        assertNotNull(s3After.admissionToken());

        WaitingRoomStatus s4After = service.status(eventId, "u4");
        assertEquals("QUEUED", s4After.status());
        assertEquals(1, s4After.position());
    }

    @Test
    void flashSaleSurgeSimulationQueuesNewUsers() {
        WaitingRoomService service = new WaitingRoomService(50, 120);
        UUID eventId = UUID.randomUUID();

        service.configureSurge(eventId, 2, 4);
        WaitingRoomStatus newcomer = service.enter(eventId, "real-customer");
        assertEquals("QUEUED", newcomer.status());
        assertEquals(5, newcomer.position());
    }
}
