package com.haolo.ticketing.invoices;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.invoices.EInvoiceModels.*;

@RestController
@RequestMapping("/api")
public class EInvoiceController {

    private final EInvoiceService invoiceService;

    public EInvoiceController(EInvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/me/bookings/{id}/e-invoice")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<EInvoiceDto> issueEInvoice(
            @PathVariable UUID id,
            @RequestBody CreateEInvoiceRequest req,
            @AuthenticationPrincipal DemoPrincipal principal) {
        EInvoiceDto dto = invoiceService.issueEInvoice(principal, id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/me/bookings/{id}/e-invoice")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<EInvoiceDto> getEInvoice(
            @PathVariable UUID id,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(invoiceService.getEInvoiceByBooking(principal, id));
    }

    @GetMapping("/e-invoices/lookup/{lookupCode}")
    public ResponseEntity<EInvoiceLookupResponse> lookupEInvoice(@PathVariable String lookupCode) {
        return ResponseEntity.ok(invoiceService.lookupEInvoice(lookupCode));
    }
}
