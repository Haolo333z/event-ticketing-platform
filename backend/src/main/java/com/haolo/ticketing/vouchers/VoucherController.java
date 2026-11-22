package com.haolo.ticketing.vouchers;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.vouchers.VoucherModels.*;

@RestController
@RequestMapping("/api")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/vouchers/validate")
    public ResponseEntity<VoucherValidationResult> validateVoucher(
            @RequestBody ValidateVoucherRequest req,
            @AuthenticationPrincipal DemoPrincipal principal) {
        UUID accountId = principal != null ? principal.accountId() : null;
        VoucherValidationResult result = voucherService.validateAndCalculate(
            accountId,
            req.code(),
            req.eventId(),
            req.ticketTypeId(),
            req.orderAmountVnd()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/bookings/{id}/apply-voucher")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApplyVoucherResponse> applyVoucher(
            @PathVariable UUID id,
            @RequestBody ApplyVoucherRequest req,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(voucherService.applyVoucherToBooking(principal.accountId(), id, req.code()));
    }

    @GetMapping("/organizer/vouchers")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<List<VoucherDto>> listOrganizerVouchers(
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(voucherService.listOrganizerVouchers(principal));
    }

    @PostMapping("/organizer/vouchers")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<VoucherDto> createVoucher(
            @RequestBody CreateVoucherRequest req,
            @AuthenticationPrincipal DemoPrincipal principal) {
        VoucherDto created = voucherService.createVoucher(principal, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
