package com.haolo.ticketing.payments;

import java.util.Map;
import java.util.UUID;

public interface PaymentGatewayPort {
    String getProvider();
    PaymentModels.PaymentInitResult initiateCheckout(PaymentModels.PaymentInitCommand cmd, UUID attemptId);
    PaymentModels.CallbackVerificationResult verifyCallback(Map<String, String> queryParams);
}
