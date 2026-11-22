package com.haolo.ticketing.payments;

import java.util.List;
import java.util.UUID;

public final class RefundProgressModels {
    private RefundProgressModels() {}

    public record RefundStep(
        int stepNumber,
        String title,
        String status, // "COMPLETED", "IN_PROGRESS", "PENDING", "REJECTED"
        String timestamp,
        String description
    ) {}

    public record RefundProgressResponse(
        UUID bookingId,
        String bookingStatus,
        long amountVnd,
        String reason,
        String currentStage,
        List<RefundStep> steps
    ) {}
}
