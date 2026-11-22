package com.haolo.ticketing.payments;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.payments.RefundProgressModels.RefundProgressResponse;

@RestController
@RequestMapping("/api/me/bookings")
public class RefundProgressController {

    private final RefundProgressService refundProgressService;

    public RefundProgressController(RefundProgressService refundProgressService) {
        this.refundProgressService = refundProgressService;
    }

    @GetMapping("/{bookingId}/refund-progress")
    public ResponseEntity<RefundProgressResponse> getRefundProgress(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable UUID bookingId
    ) {
        return ResponseEntity.ok(refundProgressService.getRefundProgress(principal, bookingId));
    }
}
