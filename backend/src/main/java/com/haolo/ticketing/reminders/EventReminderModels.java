package com.haolo.ticketing.reminders;

import java.time.Instant;
import java.util.UUID;

public final class EventReminderModels {
    private EventReminderModels() {}

    public record EventReminderDto(
        UUID id,
        UUID eventId,
        String eventTitle,
        String startsAt,
        Integer remindBeforeMinutes,
        String status,
        Instant createdAt
    ) {}

    public record CreateReminderRequest(
        Integer remindBeforeMinutes
    ) {}

    public record CreateReminderResponse(
        UUID id,
        UUID eventId,
        Integer remindBeforeMinutes,
        String status,
        String message
    ) {}
}
