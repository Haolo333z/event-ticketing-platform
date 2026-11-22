package com.haolo.ticketing.invoices;

import java.time.Instant;
import java.util.UUID;

public class EInvoiceModels {

    public record CreateEInvoiceRequest(
        String taxCode,
        String companyName,
        String companyAddress,
        String recipientEmail
    ) {}

    public record EInvoiceDto(
        UUID id,
        UUID bookingId,
        UUID accountId,
        String taxCode,
        String companyName,
        String companyAddress,
        String recipientEmail,
        String invoiceLookupCode,
        long totalAmountVnd,
        double vatRate,
        long vatAmountVnd,
        String status,
        Instant issuedAt
    ) {}

    public record EInvoiceLookupResponse(
        boolean found,
        EInvoiceDto invoice,
        String message
    ) {}
}
