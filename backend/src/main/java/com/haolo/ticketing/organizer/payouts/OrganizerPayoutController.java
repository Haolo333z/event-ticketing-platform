package com.haolo.ticketing.organizer.payouts;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.payouts.PayoutModels.*;

@RestController
@RequestMapping("/api")
public class OrganizerPayoutController {

    private final OrganizerPayoutService payoutService;

    public OrganizerPayoutController(OrganizerPayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping("/organizer/payouts/summary")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<PayoutSummaryDto> getSummary(
            @RequestParam UUID eventId,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(payoutService.getPayoutSummary(principal, eventId));
    }

    @PostMapping("/organizer/payouts/request")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<OrganizerPayoutDto> requestPayout(
            @RequestBody RequestPayoutRequest req,
            @AuthenticationPrincipal DemoPrincipal principal) {
        OrganizerPayoutDto dto = payoutService.requestPayout(principal, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/organizer/payouts")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<List<OrganizerPayoutDto>> listOrganizerPayouts(
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(payoutService.listOrganizerPayouts(principal));
    }

    @GetMapping("/admin/payouts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrganizerPayoutDto>> listAllPayouts(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(payoutService.listAllPayouts(principal, status));
    }

    @PostMapping("/admin/payouts/{id}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProcessPayoutResponse> processPayout(
            @PathVariable UUID id,
            @RequestBody ProcessPayoutRequest req,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(payoutService.processPayout(principal, id, req));
    }
}
