package com.haolo.ticketing.payments;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.haolo.ticketing.payments.PaymentModels.*;

@Component
public class PaymentSimulatorAdapter implements PaymentGatewayPort {

    @Override
    public String getProvider() {
        return "SIMULATOR";
    }

    @Override
    public PaymentInitResult initiateCheckout(PaymentInitCommand cmd, UUID attemptId) {
        Instant now = Instant.now();
        Instant expires = cmd.expiresAt() != null ? cmd.expiresAt() : now.plusSeconds(600);
        String mockGatewayUrl = "/payment-simulator?bookingId=" + cmd.bookingId()
                + "&merchantRef=" + cmd.merchantRef()
                + "&amount=" + cmd.amountVnd();

        return new PaymentInitResult(
            attemptId,
            cmd.merchantRef(),
            cmd.amountVnd(),
            "VND",
            mockGatewayUrl,
            expires.toString()
        );
    }

    @Override
    public CallbackVerificationResult verifyCallback(Map<String, String> queryParams) {
        String merchantRef = queryParams.getOrDefault("merchantRef", "");
        String providerTxRef = queryParams.getOrDefault("txRef", "SIM_" + UUID.randomUUID().toString().substring(0, 8));
        String scenario = queryParams.getOrDefault("scenario", "SUCCESS");
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(scenario);

        return new CallbackVerificationResult(
            true,
            providerTxRef,
            merchantRef,
            0L,
            isSuccess ? "00" : "01",
            isSuccess ? "Thanh toán mô phỏng thành công" : "Thanh toán mô phỏng thất bại",
            isSuccess,
            queryParams
        );
    }
}
