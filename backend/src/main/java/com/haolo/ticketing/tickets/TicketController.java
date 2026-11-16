package com.haolo.ticketing.tickets;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.haolo.ticketing.bookings.GaBookingRequest;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketQrService ticketQrService;

    public TicketController(TicketQrService ticketQrService) {
        this.ticketQrService = ticketQrService;
    }

    @GetMapping("/{id}/qr")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<TicketQrResponse> getQr(
            @AuthenticationPrincipal DemoPrincipal principal,
            @PathVariable String id) {
        UUID ticketId = GaBookingRequest.uuid(id);
        return ResponseEntity.ok(ticketQrService.getTicketQr(principal, ticketId));
    }
}
