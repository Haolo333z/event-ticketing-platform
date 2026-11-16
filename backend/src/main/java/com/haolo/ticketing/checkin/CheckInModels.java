package com.haolo.ticketing.checkin;

import java.util.UUID;

public class CheckInModels {

    public record CheckInRequest(
        String qrToken,
        String ticketCode,
        String gateName,
        String deviceInfo
    ) {}

    public record CheckInResult(
        String status,
        UUID ticketId,
        String ticketCode,
        String attendeeName,
        String ticketType,
        String seatLabel,
        String checkedInAt,
        String gate,
        String message
    ) {}

    public record CheckInStats(
        long totalSold,
        long checkedInCount,
        long remainingCount,
        double checkInRatePercent
    ) {}

    public record CheckInHistoryItem(
        UUID checkInId,
        UUID ticketId,
        String ticketCode,
        String attendeeName,
        String ticketType,
        String seatLabel,
        String gateName,
        String checkedInAt,
        String staffName
    ) {}

    public record AssignedEventDto(
        UUID eventId,
        String title,
        String venue,
        String startsAt,
        String endsAt,
        String status,
        String assignedAt
    ) {}
}
