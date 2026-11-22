package com.haolo.ticketing.payments.resilience;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/admin/payments/circuit-breaker")
public class PaymentCircuitBreakerController {

    private final PaymentCircuitBreaker circuitBreaker;

    public PaymentCircuitBreakerController(PaymentCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    private void assertAdmin(DemoPrincipal principal) {
        if (principal == null || !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ quản trị viên mới có quyền thao tác Circuit Breaker");
        }
    }

    @GetMapping
    public ResponseEntity<CircuitBreakerStatusDto> getStatus(@AuthenticationPrincipal DemoPrincipal principal) {
        assertAdmin(principal);
        return ResponseEntity.ok(circuitBreaker.getStatus());
    }

    @PostMapping("/reset")
    public ResponseEntity<CircuitBreakerStatusDto> reset(@AuthenticationPrincipal DemoPrincipal principal) {
        assertAdmin(principal);
        circuitBreaker.reset();
        return ResponseEntity.ok(circuitBreaker.getStatus());
    }

    @PostMapping("/trip")
    public ResponseEntity<CircuitBreakerStatusDto> trip(
            @AuthenticationPrincipal DemoPrincipal principal,
            @RequestBody(required = false) Map<String, String> body) {
        assertAdmin(principal);
        String reason = body != null ? body.get("reason") : "Manual trip by Admin";
        circuitBreaker.tripOpen(reason);
        return ResponseEntity.ok(circuitBreaker.getStatus());
    }
}
