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

    public record OfflineTicketItem(
        UUID ticketId,
        String ticketCode,
        String attendeeName,
        String ticketType,
        String seatLabel,
        String qrToken,
        boolean checkedIn,
        String checkedInAt
    ) {}

    public record OfflineManifestDto(
        UUID eventId,
        String title,
        String asOf,
        int totalTickets,
        java.util.List<OfflineTicketItem> tickets
    ) {}

    public record OfflineSyncItem(
        String clientSyncId,
        UUID ticketId,
        String ticketCode,
        String qrToken,
        String gateName,
        String clientTimestamp,
        String deviceInfo
    ) {}

    public record OfflineSyncRequest(
        UUID eventId,
        java.util.List<OfflineSyncItem> items
    ) {}

    public record OfflineSyncResultItem(
        String clientSyncId,
        UUID ticketId,
        String ticketCode,
        String status,
        String gateName,
        String checkedInAt,
        String message
    ) {}

    public record BatchSyncResponse(
        UUID eventId,
        String syncedAt,
        int total,
        int syncedCount,
        int conflictCount,
        java.util.List<OfflineSyncResultItem> results
    ) {}
}
