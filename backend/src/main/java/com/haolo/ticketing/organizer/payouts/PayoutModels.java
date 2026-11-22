package com.haolo.ticketing.organizer.payouts;

import java.time.Instant;
import java.util.UUID;

public class PayoutModels {

    public record PayoutSummaryDto(
        UUID eventId,
        String eventTitle,
        String saasPlan,
        double platformFeePercent,
        long grossRevenueVnd,
        long totalTicketsSold,
        long platformFeeVnd,
        long netPayoutTotalVnd,
        long totalPayoutsRequestedVnd,
        long availableBalanceVnd
    ) {}

    public record RequestPayoutRequest(
        UUID eventId,
        long amountVnd,
        String bankName,
        String bankAccountNumber,
        String bankAccountHolder
    ) {}

    public record OrganizerPayoutDto(
        UUID id,
        UUID organizerId,
        String organizationName,
        UUID eventId,
        String eventTitle,
        long grossRevenueVnd,
        double platformFeePercent,
        long platformFeeVnd,
        long netPayoutVnd,
        String bankName,
        String bankAccountNumber,
        String bankAccountHolder,
        String status,
        Instant requestedAt,
        Instant processedAt,
        String adminNotes
    ) {}

    public record ProcessPayoutRequest(
        String decision, // "APPROVE", "PAID", "REJECT"
        String adminNotes
    ) {}

    public record ProcessPayoutResponse(
        UUID payoutId,
        String status,
        Instant processedAt,
        String message
    ) {}
}
