package com.haolo.ticketing.admin;

import java.util.UUID;

public class AdminModels {

    // Refund Management
    public record RefundItemDto(
        UUID id,
        UUID bookingId,
        UUID paymentAttemptId,
        long amountVnd,
        String reason,
        String status,
        String customerEmail,
        String customerName,
        String createdAt,
        String completedAt
    ) {}

    public record ProcessRefundRequest(
        String decision, // "APPROVE" or "REJECT"
        String notes
    ) {}

    public record ProcessRefundResult(
        UUID refundId,
        String status,
        String completedAt,
        String message
    ) {}

    // Revenue & Platform Reports
    public record PlatformSummaryDto(
        long totalRevenueVnd,
        long totalTicketsSold,
        long totalConfirmedBookings,
        long totalCheckIns,
        double averageOccupancyPercent
    ) {}

    public record EventReportDto(
        UUID eventId,
        String title,
        String venue,
        String startsAt,
        String status,
        long totalCapacity,
        long soldGa,
        long soldSeated,
        long totalSold,
        long revenueVnd,
        double occupancyPercent
    ) {}

    // Enterprise Contact Leads
    public record ContactLeadDto(
        UUID id,
        String fullName,
        String email,
        String phone,
        String companyName,
        String eventScale,
        String message,
        String status,
        String notes,
        String createdAt,
        String updatedAt
    ) {}

    public record ContactLeadRequest(
        String fullName,
        String email,
        String phone,
        String companyName,
        String eventScale,
        String message
    ) {}

    public record UpdateLeadStatusRequest(
        String status,
        String notes
    ) {}

    public record PageResult<T>(
        java.util.List<T> items,
        int page,
        int size,
        int totalElements
    ) {}
}
