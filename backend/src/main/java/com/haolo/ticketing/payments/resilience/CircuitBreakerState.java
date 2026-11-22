package com.haolo.ticketing.payments.resilience;

public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
