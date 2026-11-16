package com.haolo.ticketing.payments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.haolo.ticketing.shared.ApiException;

@Component
public class PaymentGatewayRegistry {

    private final Map<String, PaymentGatewayPort> adapters = new HashMap<>();

    public PaymentGatewayRegistry(List<PaymentGatewayPort> portList) {
        for (PaymentGatewayPort port : portList) {
            adapters.put(port.getProvider().toUpperCase(), port);
        }
    }

    public PaymentGatewayPort get(String provider) {
        if (provider == null || provider.isBlank()) {
            provider = "VNPAY";
        }
        PaymentGatewayPort port = adapters.get(provider.toUpperCase());
        if (port == null) {
            throw new ApiException(400, "UNSUPPORTED_PAYMENT_PROVIDER", "Cổng thanh toán '" + provider + "' không được hỗ trợ");
        }
        return port;
    }
}
