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
}
