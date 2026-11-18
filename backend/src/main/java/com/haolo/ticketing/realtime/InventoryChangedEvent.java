package com.haolo.ticketing.realtime;

import java.time.Instant;
import java.util.UUID;

public record InventoryChangedEvent(
    UUID eventId,
    String reason,
    UUID bookingId,
    Instant occurredAt
) {}
