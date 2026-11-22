package com.haolo.ticketing.payments.resilience;

import java.time.Instant;

public record CircuitBreakerStatusDto(
    String provider,
    String state,
    int failureCount,
    int failureThreshold,
    long resetTimeoutSeconds,
    long remainingCooldownSeconds,
    String lastFailureReason,
    Instant lastChangedAt
) {}
