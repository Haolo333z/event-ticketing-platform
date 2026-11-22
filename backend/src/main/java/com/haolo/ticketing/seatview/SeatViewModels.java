package com.haolo.ticketing.seatview;

import java.util.UUID;

public final class SeatViewModels {
    private SeatViewModels() {}

    public record SeatViewDto(
        UUID seatId,
        UUID eventId,
        String seatLabel,
        String rowLabel,
        Integer seatNumber,
        Double distanceMeters,
        Double elevationAngle,
        Integer viewQualityScore,
        String viewNotes,
        String perspectiveSvg,
        String status,
        Long priceVnd,
        String ticketTypeName
    ) {}
}
