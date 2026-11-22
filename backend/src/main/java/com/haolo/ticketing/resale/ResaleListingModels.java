package com.haolo.ticketing.resale;

import java.time.Instant;
import java.util.UUID;

public class ResaleListingModels {

    public record CreateResaleListingRequest(
        UUID ticketId,
        Long askingPriceVnd
    ) {}

    public record ResaleListingDto(
        UUID id,
        UUID ticketId,
        String ticketCode,
        UUID eventId,
        String eventTitle,
        String ticketTypeTitle,
        String seatLabel,
        UUID sellerUserId,
        String sellerName,
        long originalPriceVnd,
        long askingPriceVnd,
        String status,
        UUID lockedByUserId,
        Instant lockExpiresAt,
        UUID buyerUserId,
        Instant createdAt,
        Instant soldAt
    ) {}

    public record ReserveResaleResponse(
        UUID listingId,
        Instant lockExpiresAt,
        String status,
        String message
    ) {}

    public record SettleResaleResponse(
        UUID listingId,
        UUID newTicketId,
        String newTicketCode,
        String status,
        String message
    ) {}
}
