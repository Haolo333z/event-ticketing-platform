package com.haolo.ticketing.ai;

import java.util.*;

public final class AiModels {
    private AiModels() {}

    public record ConciergeChatRequest(UUID conversationId, String message) {}

    public record SeatSuggestionCard(
        UUID eventId,
        String eventTitle,
        UUID ticketTypeId,
        String ticketTypeName,
        String mode,
        long unitPriceVnd,
        long totalPriceVnd,
        boolean contiguous,
        List<UUID> seatIds,
        List<String> seatLabels,
        int quantity,
        String reason
    ) {}

    public record ConciergeChatResponse(
        UUID conversationId,
        String reply,
        String adapterUsed,
        List<SeatSuggestionCard> suggestions,
        List<Map<String, Object>> bookingSummaries
    ) {}

    public record OrganizerGenerateRequest(
        String keywords,
        String category,
        Integer targetCapacity,
        Long basePriceVnd
    ) {}

    public record OrganizerGenerateResponse(
        String adapterUsed,
        String suggestedTitle,
        String seoDescription,
        List<String> agenda,
        List<Map<String, Object>> ticketTiers,
        Map<String, Object> seatMatrixConfig
    ) {}

    public record AdminDiagnoseRequest(UUID bookingId, UUID failureId) {}

    public record AdminDiagnoseResponse(
        String adapterUsed,
        String severity,
        String summaryVi,
        String rootCause,
        String recommendedAction,
        boolean safeToReplay,
        Map<String, Object> evidence
    ) {}

    public record SellOutForecastResponse(
        UUID eventId,
        String eventTitle,
        String status,
        int totalCapacity,
        int soldTickets,
        int heldTickets,
        int availableTickets,
        int soldLast24Hours,
        double salesVelocityPerHour,
        double heldPercentage,
        double occupancyPercentage,
        int waitlistCount,
        Double estimatedHoursToSellOut,
        String sellOutStatus,
        boolean recommendVirtualWaitingRoom,
        String recommendationVi,
        String adapterUsed
    ) {}

    public record ScalpingAlertDto(
        UUID id,
        String alertType,
        UUID eventId,
        String eventTitle,
        UUID targetAccountId,
        String targetAccountEmail,
        String targetAccountName,
        String clientIp,
        String riskLevel,
        String explanationVi,
        String status,
        Map<String, Object> evidence,
        java.time.Instant createdAt,
        java.time.Instant resolvedAt
    ) {}

    public record ScalpingActionRequest(
        String action,
        String notes
    ) {}

    public record ScalpingActionResponse(
        UUID alertId,
        String actionTaken,
        String status,
        String messageVi,
        int releasedBookingsCount
    ) {}
}
