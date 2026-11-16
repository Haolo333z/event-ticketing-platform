package com.haolo.ticketing.tickets;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketQrServiceTest {

    private JdbcTemplate jdbc;
    private TicketQrService service;
    private final String secret = "test-secret-key-12345678901234567890123456789012";

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        service = new TicketQrService(jdbc, secret);
    }

    @Test
    void testGenerateAndVerifyValidToken() {
        UUID ticketId = UUID.randomUUID();
        String ticketCode = "EF-TEST-123456";
        UUID eventId = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        String token = service.generateSignedQrToken(ticketId, ticketCode, eventId, issuedAt);
        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(5, parts.length);
        assertEquals(ticketId.toString(), parts[0]);
        assertEquals(ticketCode, parts[1]);
        assertEquals(eventId.toString(), parts[2]);
        assertEquals(String.valueOf(issuedAt.getEpochSecond()), parts[3]);
        assertFalse(parts[4].isBlank());

        assertTrue(service.verifyQrToken(token));
    }

    @Test
    void testTamperedSignatureFails() {
        UUID ticketId = UUID.randomUUID();
        String ticketCode = "EF-TEST-123456";
        UUID eventId = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        String token = service.generateSignedQrToken(ticketId, ticketCode, eventId, issuedAt);
        // Tamper signature
        String tamperedToken = token.substring(0, token.length() - 2) + "xx";
        assertFalse(service.verifyQrToken(tamperedToken));
    }

    @Test
    void testTamperedDataFails() {
        UUID ticketId = UUID.randomUUID();
        String ticketCode = "EF-TEST-123456";
        UUID eventId = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        String token = service.generateSignedQrToken(ticketId, ticketCode, eventId, issuedAt);
        String[] parts = token.split("\\.");
        // Modify ticketCode
        String tampered = parts[0] + ".EF-HACKED-999." + parts[2] + "." + parts[3] + "." + parts[4];
        assertFalse(service.verifyQrToken(tampered));
    }

    @Test
    void testInvalidTokenFormats() {
        assertFalse(service.verifyQrToken(null));
        assertFalse(service.verifyQrToken(""));
        assertFalse(service.verifyQrToken("short.token"));
        assertFalse(service.verifyQrToken("not-a-uuid.code.not-a-uuid.not-epoch.sig"));
    }

    @Test
    void testSecretMismatchFails() {
        UUID ticketId = UUID.randomUUID();
        String ticketCode = "EF-TEST-123456";
        UUID eventId = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        String token = service.generateSignedQrToken(ticketId, ticketCode, eventId, issuedAt);

        TicketQrService otherService = new TicketQrService(jdbc, "different-secret-key-12345678901234567890");
        assertFalse(otherService.verifyQrToken(token));
    }

    @Test
    void testGetTicketQrSuccessForOwner() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String ticketCode = "EF-SUCCESS-001";

        DemoPrincipal owner = new DemoPrincipal(ownerId, "CUSTOMER", List.of("CUSTOMER"));

        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", ticketCode,
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", ownerId,
                "status", "CONFIRMED"
            )
        ));

        TicketQrResponse response = service.getTicketQr(owner, ticketId);
        assertNotNull(response);
        assertEquals(ticketId, response.ticketId());
        assertEquals(ticketCode, response.ticketCode());
        assertEquals(eventId, response.eventId());
        assertTrue(service.verifyQrToken(response.qrToken()));
    }

    @Test
    void testGetTicketQrSuccessForAdmin() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String ticketCode = "EF-ADMIN-001";

        DemoPrincipal admin = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));

        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", ticketCode,
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", ownerId,
                "status", "CONFIRMED"
            )
        ));

        TicketQrResponse response = service.getTicketQr(admin, ticketId);
        assertNotNull(response);
        assertEquals(ticketId, response.ticketId());
        assertTrue(service.verifyQrToken(response.qrToken()));
    }

    @Test
    void testGetTicketQrForbiddenForNonOwner() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DemoPrincipal stranger = new DemoPrincipal(strangerId, "CUSTOMER", List.of("CUSTOMER"));

        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", "EF-STRANGER-001",
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", ownerId,
                "status", "CONFIRMED"
            )
        ));

        ApiException ex = assertThrows(ApiException.class, () -> service.getTicketQr(stranger, ticketId));
        assertEquals(403, ex.status);
        assertEquals("FORBIDDEN", ex.code);
    }

    @Test
    void testGetTicketQrUnconfirmedBookingFails() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DemoPrincipal owner = new DemoPrincipal(ownerId, "CUSTOMER", List.of("CUSTOMER"));

        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(List.of(
            Map.of(
                "id", ticketId,
                "code", "EF-PENDING-001",
                "booking_id", UUID.randomUUID(),
                "event_id", eventId,
                "account_id", ownerId,
                "status", "PENDING_PAYMENT"
            )
        ));

        ApiException ex = assertThrows(ApiException.class, () -> service.getTicketQr(owner, ticketId));
        assertEquals(400, ex.status);
        assertEquals("BAD_REQUEST", ex.code);
    }

    @Test
    void testGetTicketQrNotFound() {
        UUID ticketId = UUID.randomUUID();
        DemoPrincipal owner = new DemoPrincipal(UUID.randomUUID(), "CUSTOMER", List.of("CUSTOMER"));

        when(jdbc.queryForList(anyString(), eq(ticketId))).thenReturn(Collections.emptyList());

        ApiException ex = assertThrows(ApiException.class, () -> service.getTicketQr(owner, ticketId));
        assertEquals(404, ex.status);
        assertEquals("NOT_FOUND", ex.code);
    }

    @Test
    void testGetTicketQrUnauthenticated() {
        UUID ticketId = UUID.randomUUID();
        ApiException ex = assertThrows(ApiException.class, () -> service.getTicketQr(null, ticketId));
        assertEquals(401, ex.status);
    }
}
