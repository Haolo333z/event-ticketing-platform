package com.haolo.ticketing.payments;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.haolo.ticketing.payments.PaymentModels.*;

import static org.junit.jupiter.api.Assertions.*;

class VnPayGatewayAdapterTest {

    private VnPayGatewayAdapter adapter;
    private final String tmnCode = "DEMO_TMN";
    private final String hashSecret = "12345678901234567890123456789012";
    private final String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private final String returnUrl = "http://localhost:5173/payment-return";

    @BeforeEach
    void setUp() {
        adapter = new VnPayGatewayAdapter(tmnCode, hashSecret, payUrl, returnUrl);
    }

    @Test
    void testInitiateCheckoutGeneratesSignedUrl() {
        UUID bookingId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        String merchantRef = "EVF_TEST_123456";
        long amountVnd = 500000L;
        Instant expiresAt = Instant.now().plusSeconds(600);

        PaymentInitCommand cmd = new PaymentInitCommand(
            bookingId, "VNPAY", amountVnd, "Thanh toan ve test", "127.0.0.1", returnUrl, merchantRef, expiresAt
        );

        PaymentInitResult result = adapter.initiateCheckout(cmd, attemptId);
        assertNotNull(result);
        assertEquals(attemptId, result.attemptId());
        assertEquals(merchantRef, result.merchantRef());
        assertEquals(amountVnd, result.amountVnd());
        assertNotNull(result.gatewayUrl());

        assertTrue(result.gatewayUrl().startsWith(payUrl));
        assertTrue(result.gatewayUrl().contains("vnp_TmnCode=" + tmnCode));
        assertTrue(result.gatewayUrl().contains("vnp_Amount=50000000")); // 500,000 * 100
        assertTrue(result.gatewayUrl().contains("vnp_TxnRef=" + merchantRef));
        assertTrue(result.gatewayUrl().contains("vnp_SecureHash="));
    }

    @Test
    void testVerifyValidCallbackSuccess() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", "25000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan ve");
        params.put("vnp_PayDate", "20261116120000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14589201");
        params.put("vnp_TxnRef", "EVF_REF_001");

        // Compute valid signature
        StringBuilder hashData = new StringBuilder();
        new java.util.TreeMap<>(params).forEach((k, v) -> {
            try {
                String ek = java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.US_ASCII.toString());
                String ev = java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.US_ASCII.toString());
                hashData.append(ek).append('=').append(ev).append('&');
            } catch (Exception ignored) {}
        });
        hashData.setLength(hashData.length() - 1);
        String validHash = VnPayGatewayAdapter.hmacSha512(hashSecret, hashData.toString());

        params.put("vnp_SecureHash", validHash);

        CallbackVerificationResult result = adapter.verifyCallback(params);
        assertTrue(result.isValidSignature());
        assertTrue(result.isSuccess());
        assertEquals("00", result.responseCode());
        assertEquals(250000L, result.amountVnd());
        assertEquals("EVF_REF_001", result.merchantRef());
        assertEquals("14589201", result.providerTxRef());
    }

    @Test
    void testVerifyCustomerCancelledCallback() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", "25000000");
        params.put("vnp_ResponseCode", "24"); // Khach huy
        params.put("vnp_TxnRef", "EVF_REF_CANCEL");
        params.put("vnp_TransactionNo", "0");

        StringBuilder hashData = new StringBuilder();
        new java.util.TreeMap<>(params).forEach((k, v) -> {
            try {
                String ek = java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.US_ASCII.toString());
                String ev = java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.US_ASCII.toString());
                hashData.append(ek).append('=').append(ev).append('&');
            } catch (Exception ignored) {}
        });
        hashData.setLength(hashData.length() - 1);
        String validHash = VnPayGatewayAdapter.hmacSha512(hashSecret, hashData.toString());

        params.put("vnp_SecureHash", validHash);

        CallbackVerificationResult result = adapter.verifyCallback(params);
        assertTrue(result.isValidSignature(), "Signature must still be valid even if customer cancelled");
        assertFalse(result.isSuccess(), "isSuccess must be false for non-00 responseCode");
        assertEquals("24", result.responseCode());
    }

    @Test
    void testVerifyTamperedSignatureFails() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", "25000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", "EVF_REF_TAMPERED");
        params.put("vnp_SecureHash", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");

        CallbackVerificationResult result = adapter.verifyCallback(params);
        assertFalse(result.isValidSignature());
        assertFalse(result.isSuccess());
    }
}
