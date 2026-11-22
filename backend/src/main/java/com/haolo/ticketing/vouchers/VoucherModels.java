package com.haolo.ticketing.vouchers;

import java.time.Instant;
import java.util.UUID;

public class VoucherModels {

    public record VoucherDto(
        UUID id,
        UUID organizerId,
        UUID eventId,
        UUID ticketTypeId,
        String code,
        String discountType,
        long discountValue,
        long minOrderAmountVnd,
        Long maxDiscountAmountVnd,
        int maxUses,
        int usedCount,
        Instant validFrom,
        Instant validTo,
        boolean isActive,
        Instant createdAt
    ) {}

    public record ValidateVoucherRequest(
        String code,
        UUID eventId,
        UUID ticketTypeId,
        long orderAmountVnd
    ) {}

    public record VoucherValidationResult(
        boolean valid,
        UUID voucherId,
        String code,
        String discountType,
        long discountValue,
        long discountAmountVnd,
        long finalAmountVnd,
        String message
    ) {}

    public record ApplyVoucherRequest(
        String code
    ) {}

    public record ApplyVoucherResponse(
        UUID bookingId,
        UUID voucherId,
        String code,
        long originalAmountVnd,
        long discountAmountVnd,
        long finalAmountVnd,
        String message
    ) {}

    public record CreateVoucherRequest(
        UUID eventId,
        UUID ticketTypeId,
        String code,
        String discountType, // PERCENTAGE or FIXED_VND
        long discountValue,
        long minOrderAmountVnd,
        Long maxDiscountAmountVnd,
        int maxUses,
        Instant validFrom,
        Instant validTo
    ) {}
}
