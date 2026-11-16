package com.haolo.ticketing.tickets;

import java.util.UUID;

public record TicketQrResponse(
    UUID ticketId,
    String ticketCode,
    UUID eventId,
    String qrToken,
    String issuedAt
) {}
