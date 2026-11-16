package com.haolo.ticketing.payments;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class PaymentModels {

    public record PaymentInitCommand(
        UUID bookingId,
        String provider,
        long amountVnd,
        String orderInfo,
        String ipAddr,
        String returnUrl,
        String merchantRef,
        Instant expiresAt
    ) {}

    public record PaymentInitResult(
        UUID attemptId,
        String merchantRef,
        long amountVnd,
        String currency,
        String gatewayUrl,
        String expiresAt
    ) {}

    public record CallbackVerificationResult(
        boolean isValidSignature,
        String providerTxRef,
        String merchantRef,
        long amountVnd,
        String responseCode,
        String message,
        boolean isSuccess,
        Map<String, String> rawParams
    ) {}

    public record CheckoutRequest(
        UUID bookingId,
        String provider,
        String returnUrl
    ) {}

    public record PaymentStatusResponse(
        UUID bookingId,
        String status,
        long totalAmountVnd,
        String provider,
        String merchantRef,
        String gatewayUrl,
        String expiresAt
    ) {}
}
