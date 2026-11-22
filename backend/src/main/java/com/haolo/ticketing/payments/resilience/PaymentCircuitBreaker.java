package com.haolo.ticketing.payments.resilience;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(PaymentCircuitBreaker.class);

    private final String provider = "VNPAY";
    private final int failureThreshold;
    private final long resetTimeoutSeconds;

    private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTimeMillis = new AtomicLong(0);
    private volatile Instant lastChangedAt = Instant.now();
    private volatile String lastFailureReason = null;

    public PaymentCircuitBreaker(
            @Value("${eventflow.payment.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${eventflow.payment.circuit-breaker.reset-timeout-seconds:30}") long resetTimeoutSeconds) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.resetTimeoutSeconds = Math.max(1, resetTimeoutSeconds);
    }

    public CircuitBreakerState getState() {
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.OPEN) {
            long elapsedMillis = System.currentTimeMillis() - lastFailureTimeMillis.get();
            if (elapsedMillis >= resetTimeoutSeconds * 1000L) {
                if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    lastChangedAt = Instant.now();
                    log.info("Circuit breaker for {} transitioned from OPEN to HALF_OPEN (probing gateway)", provider);
                    return CircuitBreakerState.HALF_OPEN;
                }
            }
        }
        return state.get();
    }

    public boolean allowRequest() {
        CircuitBreakerState current = getState();
        return current == CircuitBreakerState.CLOSED || current == CircuitBreakerState.HALF_OPEN;
    }

    public void recordSuccess() {
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.HALF_OPEN) {
            state.set(CircuitBreakerState.CLOSED);
            failureCount.set(0);
            lastChangedAt = Instant.now();
            lastFailureReason = null;
            log.info("Circuit breaker for {} transitioned from HALF_OPEN to CLOSED (gateway recovered)", provider);
        } else if (current == CircuitBreakerState.CLOSED) {
            failureCount.set(0);
        }
    }

    public void recordFailure(String reason) {
        lastFailureTimeMillis.set(System.currentTimeMillis());
        lastFailureReason = reason;
        CircuitBreakerState current = state.get();

        if (current == CircuitBreakerState.HALF_OPEN) {
            state.set(CircuitBreakerState.OPEN);
            lastChangedAt = Instant.now();
            log.warn("Circuit breaker for {} failed in HALF_OPEN, transitioned back to OPEN: {}", provider, reason);
        } else if (current == CircuitBreakerState.CLOSED) {
            int currentFailures = failureCount.incrementAndGet();
            if (currentFailures >= failureThreshold) {
                state.set(CircuitBreakerState.OPEN);
                lastChangedAt = Instant.now();
                log.error("Circuit breaker for {} tripped OPEN after {} consecutive failures: {}", provider, currentFailures, reason);
            }
        }
    }

    public void reset() {
        state.set(CircuitBreakerState.CLOSED);
        failureCount.set(0);
        lastFailureTimeMillis.set(0);
        lastFailureReason = null;
        lastChangedAt = Instant.now();
        log.info("Circuit breaker for {} manually reset to CLOSED", provider);
    }

    public void tripOpen(String reason) {
        lastFailureTimeMillis.set(System.currentTimeMillis());
        lastFailureReason = reason != null && !reason.isBlank() ? reason : "Manual trip by Admin";
        state.set(CircuitBreakerState.OPEN);
        failureCount.set(failureThreshold);
        lastChangedAt = Instant.now();
        log.warn("Circuit breaker for {} manually tripped to OPEN: {}", provider, lastFailureReason);
    }

    public long getRemainingCooldownSeconds() {
        if (state.get() != CircuitBreakerState.OPEN) {
            return 0;
        }
        long elapsedSec = (System.currentTimeMillis() - lastFailureTimeMillis.get()) / 1000L;
        return Math.max(0, resetTimeoutSeconds - elapsedSec);
    }

    public CircuitBreakerStatusDto getStatus() {
        CircuitBreakerState current = getState();
        return new CircuitBreakerStatusDto(
            provider,
            current.name(),
            failureCount.get(),
            failureThreshold,
            resetTimeoutSeconds,
            getRemainingCooldownSeconds(),
            lastFailureReason,
            lastChangedAt
        );
    }
}
