package com.haolo.ticketing.tickets;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketQrIntegrationTest {

    private JdbcTemplate jdbc;
    private TicketQrService ticketQrService;
    private TicketController ticketController;

    private final UUID customerAId = UUID.randomUUID();
    private final UUID customerBId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private final DemoPrincipal customerA = new DemoPrincipal(customerAId, "CUSTOMER", List.of("CUSTOMER"));
    private final DemoPrincipal customerB = new DemoPrincipal(customerBId, "CUSTOMER", List.of("CUSTOMER"));
    private final DemoPrincipal admin = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));

    private final UUID ticketId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final String ticketCode = "EF-2026-CUSTOMER-A-01";

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        ticketQrService = new TicketQrService(jdbc, "integration-test-qr-secret-key-32-bytes!!");
        ticketController = new TicketController(ticketQrService);
    }

    @Test
    void customerCanAccessTheirOwnTicketQr() {
        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", ticketCode,
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", customerAId,
                "status", "CONFIRMED"
            )
        ));

        ResponseEntity<TicketQrResponse> res = ticketController.getQr(customerA, ticketId.toString());
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(ticketId, res.getBody().ticketId());
        assertEquals(ticketCode, res.getBody().ticketCode());
        assertEquals(eventId, res.getBody().eventId());

        // Verify signed opaque token
        String qrToken = res.getBody().qrToken();
        assertTrue(ticketQrService.verifyQrToken(qrToken));
    }

    @Test
    void customerCannotAccessAnotherCustomerTicketQr() {
        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", ticketCode,
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", customerAId, // belongs to Customer A
                "status", "CONFIRMED"
            )
        ));

        ApiException ex = assertThrows(ApiException.class, () -> ticketController.getQr(customerB, ticketId.toString()));
        assertEquals(403, ex.status);
        assertEquals("FORBIDDEN", ex.code);
    }

    @Test
    void adminCanAccessAnyCustomerTicketQr() {
        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", ticketCode,
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", customerAId, // belongs to Customer A
                "status", "CONFIRMED"
            )
        ));

        ResponseEntity<TicketQrResponse> res = ticketController.getQr(admin, ticketId.toString());
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(ticketId, res.getBody().ticketId());
        assertTrue(ticketQrService.verifyQrToken(res.getBody().qrToken()));
    }

    @Test
    void cannotGetQrIfBookingNotConfirmed() {
        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", ticketCode,
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", customerAId,
                "status", "PENDING_PAYMENT"
            )
        ));

        ApiException ex = assertThrows(ApiException.class, () -> ticketController.getQr(customerA, ticketId.toString()));
        assertEquals(400, ex.status);
        assertEquals("BAD_REQUEST", ex.code);
    }

    @Test
    void cannotGetQrIfBookingExpired() {
        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", ticketCode,
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", customerAId,
                "status", "EXPIRED"
            )
        ));

        ApiException ex = assertThrows(ApiException.class, () -> ticketController.getQr(customerA, ticketId.toString()));
        assertEquals(400, ex.status);
        assertEquals("BAD_REQUEST", ex.code);
    }
}
