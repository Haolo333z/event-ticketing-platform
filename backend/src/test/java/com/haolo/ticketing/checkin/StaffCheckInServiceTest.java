package com.haolo.ticketing.checkin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.checkin.CheckInModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.tickets.TicketQrService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffCheckInServiceTest {

    private JdbcTemplate jdbc;
    private TicketQrService qrService;
    private StaffCheckInService service;

    private final UUID staffId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();

    private final DemoPrincipal staffPrincipal = new DemoPrincipal(staffId, "STAFF", List.of("STAFF"));
    private final DemoPrincipal adminPrincipal = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));
    private final DemoPrincipal customerPrincipal = new DemoPrincipal(customerId, "CUSTOMER", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        qrService = Mockito.mock(TicketQrService.class);
        service = new StaffCheckInService(jdbc, qrService);
    }

    @Test
    void unauthenticatedThrows401() {
        ApiException ex = assertThrows(ApiException.class, () -> service.getAssignedEvents(null));
        assertEquals(401, ex.status);

        ex = assertThrows(ApiException.class, () -> service.checkIn(null, eventId, new CheckInRequest(null, "CODE", "GATE_A", null)));
        assertEquals(401, ex.status);
    }

    @Test
    void customerRoleDeniedFromStaffCheckIn() {
        ApiException ex = assertThrows(ApiException.class, () -> service.getAssignedEvents(customerPrincipal));
        assertEquals(403, ex.status);

        ex = assertThrows(ApiException.class, () -> service.checkIn(customerPrincipal, eventId, new CheckInRequest(null, "CODE", "GATE_A", null)));
        assertEquals(403, ex.status);
    }

    @Test
    void staffNotAssignedToEventDenied403() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(staffId), eq(eventId))).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> service.checkIn(staffPrincipal, eventId, new CheckInRequest(null, "CODE", "GATE_A", null)));
        assertEquals(403, ex.status);
        assertEquals("EVENT_SCOPE_DENIED", ex.code);
    }

    @Test
    void adminBypassesEventScopeCheck() {
        // Admin does not need staff assignment
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        // Fails with 400 because no QR/code provided
        ApiException ex = assertThrows(ApiException.class, () -> service.checkIn(adminPrincipal, eventId, new CheckInRequest(null, null, "GATE_A", null)));
        assertEquals(400, ex.status);
        assertEquals("VALIDATION_ERROR", ex.code);
    }

    @Test
    void checkInFailsIfInvalidQrToken() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(staffId), eq(eventId))).thenReturn(1);
        when(qrService.verifyQrToken("invalid.token")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.checkIn(staffPrincipal, eventId, new CheckInRequest("invalid.token", null, "GATE_A", null)));
        assertEquals(400, ex.status);
        assertEquals("INVALID_QR_TOKEN", ex.code);
    }

    @Test
    void checkInFailsIfEventMismatchInQrToken() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(staffId), eq(eventId))).thenReturn(1);
        UUID differentEventId = UUID.randomUUID();
        String validOtherEventToken = ticketId + ".TK-123." + differentEventId + ".1700000000.sig";
        when(qrService.verifyQrToken(validOtherEventToken)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.checkIn(staffPrincipal, eventId, new CheckInRequest(validOtherEventToken, null, "GATE_A", null)));
        assertEquals(400, ex.status);
        assertEquals("EVENT_MISMATCH", ex.code);
    }

    @Test
    void checkInFailsIfTicketNotFound() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(staffId), eq(eventId))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq("TK-NONEXIST"))).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.checkIn(staffPrincipal, eventId, new CheckInRequest(null, "TK-NONEXIST", "GATE_A", null)));
        assertEquals(404, ex.status);
        assertEquals("NOT_FOUND", ex.code);
    }

    @Test
    void checkInFailsIfBookingNotConfirmed() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(staffId), eq(eventId))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq("TK-PENDING"))).thenReturn(List.of(
            Map.of(
                "ticket_id", ticketId,
                "ticket_code", "TK-PENDING",
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "booking_status", "PENDING_PAYMENT",
                "attendee_name", "Nguyễn Văn A",
                "ticket_type_name", "Vé VIP",
                "seat_label", "A-01"
            )
        ));

        ApiException ex = assertThrows(ApiException.class, () -> service.checkIn(staffPrincipal, eventId, new CheckInRequest(null, "TK-PENDING", "GATE_A", null)));
        assertEquals(400, ex.status);
        assertEquals("TICKET_NOT_CONFIRMED", ex.code);
    }

    @Test
    void checkInSuccessReturnsCheckedInResult() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(staffId), eq(eventId))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq("TK-VALID-01"))).thenReturn(List.of(
            Map.of(
                "ticket_id", ticketId,
                "ticket_code", "TK-VALID-01",
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "booking_status", "CONFIRMED",
                "attendee_name", "Trần Thị B",
                "ticket_type_name", "Vé Phổ Thông",
                "seat_label", "B-15"
            )
        ));

        Timestamp now = Timestamp.from(Instant.now());
        when(jdbc.queryForObject(contains("INSERT INTO ticketing.ticket_check_ins"), eq(Timestamp.class), any(), any(), any(), any(), any()))
            .thenReturn(now);

        CheckInResult result = service.checkIn(staffPrincipal, eventId, new CheckInRequest(null, "TK-VALID-01", "GATE_MAIN", "Scanner-01"));

        assertNotNull(result);
        assertEquals("CHECKED_IN", result.status());
        assertEquals(ticketId, result.ticketId());
        assertEquals("TK-VALID-01", result.ticketCode());
        assertEquals("Trần Thị B", result.attendeeName());
        assertEquals("B-15", result.seatLabel());
        assertEquals("GATE_MAIN", result.gate());
        assertEquals("HỢP LỆ — MỜI VÀO", result.message());
    }

    @Test
    void doubleCheckInThrows409ConflictWithPreviousCheckInDetails() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(staffId), eq(eventId))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq("TK-DUPLICATE"))).thenReturn(List.of(
            Map.of(
                "ticket_id", ticketId,
                "ticket_code", "TK-DUPLICATE",
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "booking_status", "CONFIRMED",
                "attendee_name", "Lê Văn C",
                "ticket_type_name", "Vé VIP",
                "seat_label", "VIP-1"
            )
        ));

        // Simulate unique constraint violation
        when(jdbc.queryForObject(contains("INSERT INTO ticketing.ticket_check_ins"), eq(Timestamp.class), any(), any(), any(), any(), any()))
            .thenThrow(new DuplicateKeyException("uq_ticket_checked_in"));

        Timestamp firstScan = Timestamp.from(Instant.parse("2026-11-16T19:30:00Z"));
        when(jdbc.queryForList(contains("SELECT c.checked_in_at"), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "checked_in_at", firstScan,
                "gate_name", "GATE_A",
                "staff_name", "Staff Gate A"
            )
        ));

        ApiException ex = assertThrows(ApiException.class, () -> service.checkIn(staffPrincipal, eventId, new CheckInRequest(null, "TK-DUPLICATE", "GATE_B", null)));
        assertEquals(409, ex.status);
        assertEquals("TICKET_ALREADY_USED", ex.code);
        assertTrue(ex.getMessage().contains("GATE_A"));
        assertTrue(ex.getMessage().contains("Staff Gate A"));
    }

    @Test
    void getStatsCalculatesAccuratePercentages() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(staffId), eq(eventId))).thenReturn(1);
        when(jdbc.queryForObject(contains("SELECT count(t.id)"), eq(Long.class), eq(eventId))).thenReturn(100L);
        when(jdbc.queryForObject(contains("SELECT count(c.id)"), eq(Long.class), eq(eventId))).thenReturn(75L);

        CheckInStats stats = service.getStats(staffPrincipal, eventId);
        assertNotNull(stats);
        assertEquals(100L, stats.totalSold());
        assertEquals(75L, stats.checkedInCount());
        assertEquals(25L, stats.remainingCount());
        assertEquals(75.0, stats.checkInRatePercent());
    }
}
