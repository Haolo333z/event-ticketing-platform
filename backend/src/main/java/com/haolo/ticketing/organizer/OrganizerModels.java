package com.haolo.ticketing.organizer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrganizerModels {

    public record OrganizerProfile(
        UUID id,
        UUID userId,
        String organizationName,
        String contactEmail,
        String phone,
        String taxCode,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record OrganizerProfileRequest(
        String organizationName,
        String contactEmail,
        String phone,
        String taxCode
    ) {}

    public record VenueDto(
        UUID id,
        String name,
        String address,
        String city,
        String country,
        int capacity,
        String seatingLayoutJson
    ) {}

    public record EventCategoryDto(
        String id,
        String name,
        String description,
        String iconUrl
    ) {}

    public record SeatDto(
        UUID id,
        String label,
        String status
    ) {}

    public record TicketTypeDto(
        UUID id,
        String name,
        String mode,
        long priceVnd,
        boolean active,
        Long capacity,
        Integer available,
        List<SeatDto> seats
    ) {}

    public record OrganizerEventDto(
        UUID id,
        String title,
        String description,
        String venue,
        UUID venueId,
        String categoryId,
        UUID organizerId,
        Instant saleStartAt,
        Instant saleEndAt,
        Instant startsAt,
        String status,
        List<TicketTypeDto> ticketTypes,
        long totalCapacity,
        long soldTickets,
        Instant createdAt
    ) {}

    public record CreateOrganizerEventRequest(
        String title,
        String description,
        String venue,
        UUID venueId,
        String categoryId,
        Instant saleStartAt,
        Instant saleEndAt,
        Instant startsAt
    ) {}

    public record UpdateOrganizerEventRequest(
        String title,
        String description,
        String venue,
        UUID venueId,
        String categoryId,
        Instant saleStartAt,
        Instant saleEndAt,
        Instant startsAt
    ) {}

    public record AddTicketTypeRequest(
        String name,
        String mode,
        long priceVnd,
        Long capacity,
        List<String> seatLabels
    ) {}

    public record AssignStaffRequest(
        UUID staffUserId,
        String staffEmail
    ) {}

    public record StaffAssignmentDto(
        UUID id,
        UUID eventId,
        UUID staffUserId,
        String staffName,
        String staffEmail,
        UUID assignedBy,
        Instant assignedAt,
        Instant revokedAt
    ) {}

    public record StaffCandidateDto(
        UUID id,
        String displayName,
        String email,
        String phone
    ) {}
}
