package com.haolo.ticketing.checkin;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.haolo.ticketing.checkin.CheckInModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffCheckInControllerTest {

    private StaffCheckInService service;
    private StaffCheckInController controller;

    private final UUID staffId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final DemoPrincipal staffPrincipal = new DemoPrincipal(staffId, "STAFF", List.of("STAFF"));

    @BeforeEach
    void setUp() {
        service = Mockito.mock(StaffCheckInService.class);
        controller = new StaffCheckInController(service);
    }

    @Test
    void getAssignedEventsDelegatesToService() {
        List<AssignedEventDto> events = List.of(
            new AssignedEventDto(eventId, "Đại Nhạc Hội Mùa Thu", "Sân Mỹ Đình", "2026-11-20T19:00:00Z", "2026-11-20T22:00:00Z", "PUBLISHED", "2026-11-16T10:00:00Z")
        );
        when(service.getAssignedEvents(staffPrincipal)).thenReturn(events);

        ResponseEntity<List<AssignedEventDto>> res = controller.getAssignedEvents(staffPrincipal);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().size());
        assertEquals("Đại Nhạc Hội Mùa Thu", res.getBody().getFirst().title());
    }

    @Test
    void checkInDelegatesToService() {
        CheckInRequest req = new CheckInRequest("VALID.QR.TOKEN", null, "GATE_VIP", "Scanner-01");
        CheckInResult expected = new CheckInResult("CHECKED_IN", ticketId, "TK-01", "Khách VIP", "VIP", "VIP-1", "2026-11-16T19:00:00Z", "GATE_VIP", "HỢP LỆ — MỜI VÀO");

        when(service.checkIn(staffPrincipal, eventId, req)).thenReturn(expected);

        ResponseEntity<CheckInResult> res = controller.checkIn(eventId, req, staffPrincipal);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("CHECKED_IN", res.getBody().status());
        assertEquals("VIP-1", res.getBody().seatLabel());
        assertEquals("HỢP LỆ — MỜI VÀO", res.getBody().message());
    }

    @Test
    void getStatsDelegatesToService() {
        CheckInStats expected = new CheckInStats(500, 350, 150, 70.0);
        when(service.getStats(staffPrincipal, eventId)).thenReturn(expected);

        ResponseEntity<CheckInStats> res = controller.getStats(eventId, staffPrincipal);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(500, res.getBody().totalSold());
        assertEquals(350, res.getBody().checkedInCount());
        assertEquals(150, res.getBody().remainingCount());
        assertEquals(70.0, res.getBody().checkInRatePercent());
    }

    @Test
    void getHistoryDelegatesToService() {
        List<CheckInHistoryItem> items = List.of(
            new CheckInHistoryItem(UUID.randomUUID(), ticketId, "TK-01", "Nguyễn Văn A", "VIP", "VIP-1", "GATE_A", "2026-11-16T19:00:00Z", "Staff 1")
        );
        when(service.getRecentHistory(staffPrincipal, eventId, 50)).thenReturn(items);

        ResponseEntity<List<CheckInHistoryItem>> res = controller.getHistory(eventId, 50, staffPrincipal);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().size());
        assertEquals("Nguyễn Văn A", res.getBody().getFirst().attendeeName());
    }
}
