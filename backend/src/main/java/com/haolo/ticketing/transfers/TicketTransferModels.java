package com.haolo.ticketing.transfers;

import java.time.Instant;
import java.util.UUID;

public final class TicketTransferModels {
    private TicketTransferModels() {}

    public record InitiateTransferRequest(
        UUID ticketId,
        String toEmail,
        String note
    ) {}

    public record TransferDetailDto(
        UUID id,
        UUID ticketId,
        String ticketCode,
        String eventTitle,
        String startsAt,
        String seatLabel,
        String fromEmail,
        String fromName,
        String toEmail,
        String note,
        String status,
        String transferToken,
        Instant expiresAt,
        Instant createdAt
    ) {}

    public record AcceptTransferResponse(
        UUID transferId,
        UUID oldTicketId,
        UUID newTicketId,
        String newTicketCode,
        String status,
        String message
    ) {}

    public record TransferItemDto(
        UUID id,
        UUID ticketId,
        String ticketCode,
        String eventTitle,
        String toEmail,
        String status,
        String transferToken,
        String note,
        Instant expiresAt,
        Instant createdAt
    ) {}
}
