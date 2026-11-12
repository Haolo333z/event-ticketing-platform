package com.haolo.ticketing.bookings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record BookingPolicy(int lockMs,int statementMs,int attemptMs,int totalMs,int poolMs,int holdSeconds,int maxQuantity) {
    public BookingPolicy(@Value("${BOOKING_LOCK_TIMEOUT_MS:1500}") int lockMs,
        @Value("${BOOKING_STATEMENT_TIMEOUT_MS:5000}") int statementMs,
        @Value("${BOOKING_ATTEMPT_TIMEOUT_MS:5000}") int attemptMs,
        @Value("${BOOKING_TOTAL_TIMEOUT_MS:10000}") int totalMs,
        @Value("${spring.datasource.hikari.connection-timeout:2000}") int poolMs,
        @Value("${BOOKING_HOLD_TTL_SECONDS:600}") int holdSeconds,
        @Value("${BOOKING_MAX_QUANTITY:6}") int maxQuantity) {
        if(lockMs<1||lockMs>=statementMs||attemptMs<1000||statementMs>attemptMs||attemptMs>totalMs||totalMs>10000||poolMs<1||poolMs+1000>=totalMs||holdSeconds<1||maxQuantity<1||maxQuantity>6)
            throw new IllegalArgumentException("Invalid booking timeout/hold policy");
        this.lockMs=lockMs;this.statementMs=statementMs;this.attemptMs=attemptMs;this.totalMs=totalMs;this.poolMs=poolMs;this.holdSeconds=holdSeconds;this.maxQuantity=maxQuantity;
    }
}
