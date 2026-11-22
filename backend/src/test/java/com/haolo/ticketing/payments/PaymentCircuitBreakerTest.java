package com.haolo.ticketing.payments;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.haolo.ticketing.payments.resilience.CircuitBreakerState;
import com.haolo.ticketing.payments.resilience.PaymentCircuitBreaker;

class PaymentCircuitBreakerTest {

    private PaymentCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new PaymentCircuitBreaker(3, 1); // 3 failures threshold, 1 second cooldown
    }

    @Test
    void initialStateIsClosedAndAllowsRequests() {
        assertEquals(CircuitBreakerState.CLOSED, breaker.getState());
        assertTrue(breaker.allowRequest());
        assertEquals(0, breaker.getStatus().failureCount());
    }

    @Test
    void failuresBelowThresholdRemainClosed() {
        breaker.recordFailure("Timeout 1");
        breaker.recordFailure("Timeout 2");

        assertEquals(CircuitBreakerState.CLOSED, breaker.getState());
        assertTrue(breaker.allowRequest());
        assertEquals(2, breaker.getStatus().failureCount());

        breaker.recordSuccess();
        assertEquals(0, breaker.getStatus().failureCount());
    }

    @Test
    void consecutiveFailuresReachThresholdTripOpen() {
        breaker.recordFailure("Connection error 1");
        breaker.recordFailure("Connection error 2");
        breaker.recordFailure("Connection error 3");

        assertEquals(CircuitBreakerState.OPEN, breaker.getState());
        assertFalse(breaker.allowRequest());
        assertEquals(3, breaker.getStatus().failureCount());
        assertEquals("Connection error 3", breaker.getStatus().lastFailureReason());
    }

    @Test
    void manualTripAndResetWorkAsExpected() {
        breaker.tripOpen("Maintenance window");
        assertEquals(CircuitBreakerState.OPEN, breaker.getState());
        assertFalse(breaker.allowRequest());
        assertEquals("Maintenance window", breaker.getStatus().lastFailureReason());

        breaker.reset();
        assertEquals(CircuitBreakerState.CLOSED, breaker.getState());
        assertTrue(breaker.allowRequest());
        assertEquals(0, breaker.getStatus().failureCount());
    }

    @Test
    void halfOpenTransitionAndRecovery() throws InterruptedException {
        breaker.recordFailure("Fail 1");
        breaker.recordFailure("Fail 2");
        breaker.recordFailure("Fail 3");
        assertEquals(CircuitBreakerState.OPEN, breaker.getState());

        // Wait for 1 second cooldown
        Thread.sleep(1100);

        // Accessing state should trigger transition to HALF_OPEN
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.getState());
        assertTrue(breaker.allowRequest());

        // Successful probe request restores CLOSED
        breaker.recordSuccess();
        assertEquals(CircuitBreakerState.CLOSED, breaker.getState());
        assertEquals(0, breaker.getStatus().failureCount());
    }

    @Test
    void halfOpenFailureReopensCircuit() throws InterruptedException {
        breaker.recordFailure("Fail 1");
        breaker.recordFailure("Fail 2");
        breaker.recordFailure("Fail 3");
        assertEquals(CircuitBreakerState.OPEN, breaker.getState());

        Thread.sleep(1100);
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.getState());

        // Failure during HALF_OPEN should immediately reopen
        breaker.recordFailure("Probe failed");
        assertEquals(CircuitBreakerState.OPEN, breaker.getState());
        assertFalse(breaker.allowRequest());
    }
}
