package com.haolo.ticketing.tickets;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.haolo.ticketing.identity.DemoPrincipal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketControllerTest {

    private TicketQrService service;
    private TicketController controller;

    private final UUID userId = UUID.randomUUID();
    private final DemoPrincipal principal = new DemoPrincipal(userId, "CUSTOMER", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        service = Mockito.mock(TicketQrService.class);
        controller = new TicketController(service);
    }

    @Test
    void testGetQrEndpoint() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TicketQrResponse mockResponse = new TicketQrResponse(
            ticketId, "EF-123", eventId, "mock.qr.token.string", Instant.now().toString()
        );

        when(service.getTicketQr(eq(principal), eq(ticketId))).thenReturn(mockResponse);

        ResponseEntity<TicketQrResponse> result = controller.getQr(principal, ticketId.toString());
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(ticketId, result.getBody().ticketId());
        assertEquals("EF-123", result.getBody().ticketCode());
        assertEquals("mock.qr.token.string", result.getBody().qrToken());
    }
}
