package com.haolo.ticketing.payments;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.haolo.ticketing.payments.PaymentModels.*;

@Component
public class VnPayGatewayAdapter implements PaymentGatewayPort {

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String defaultReturnUrl;

    private static final DateTimeFormatter VNPAY_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public VnPayGatewayAdapter(
            @Value("${eventflow.payment.vnpay.tmn-code:DEMO_TMN}") String tmnCode,
            @Value("${eventflow.payment.vnpay.hash-secret:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}") String hashSecret,
            @Value("${eventflow.payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${eventflow.payment.vnpay.return-url:http://localhost:5173/payment-return}") String defaultReturnUrl) {
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
        this.defaultReturnUrl = defaultReturnUrl;
    }

    @Override
    public String getProvider() {
        return "VNPAY";
    }

    @Override
    public PaymentInitResult initiateCheckout(PaymentInitCommand cmd, UUID attemptId) {
        Map<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", tmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(cmd.amountVnd() * 100L));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", cmd.merchantRef());
        vnpParams.put("vnp_OrderInfo", cmd.orderInfo() != null ? cmd.orderInfo() : "Thanh toan don hang EventFlow " + cmd.merchantRef());
        vnpParams.put("vnp_OrderType", "billpayment");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", (cmd.returnUrl() != null && !cmd.returnUrl().isBlank()) ? cmd.returnUrl() : defaultReturnUrl);
        vnpParams.put("vnp_IpAddr", (cmd.ipAddr() != null && !cmd.ipAddr().isBlank()) ? cmd.ipAddr() : "127.0.0.1");

        Instant now = Instant.now();
        vnpParams.put("vnp_CreateDate", VNPAY_DATE_FORMAT.format(now));
        Instant expires = cmd.expiresAt() != null ? cmd.expiresAt() : now.plusSeconds(900);
        vnpParams.put("vnp_ExpireDate", VNPAY_DATE_FORMAT.format(expires));

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (Map.Entry<String, String> entry : vnpParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                String encodedKey = encode(key);
                String encodedValue = encode(value);

                hashData.append(encodedKey).append('=').append(encodedValue).append('&');
                query.append(encodedKey).append('=').append(encodedValue).append('&');
            }
        }

        // Remove trailing '&' from hashData
        if (!hashData.isEmpty()) {
            hashData.setLength(hashData.length() - 1);
        }

        String secureHash = hmacSha512(hashSecret, hashData.toString());
        query.append("vnp_SecureHash=").append(secureHash);

        String gatewayUrl = payUrl + "?" + query;

        return new PaymentInitResult(
            attemptId,
            cmd.merchantRef(),
            cmd.amountVnd(),
            "VND",
            gatewayUrl,
            expires.toString()
        );
    }

    @Override
    public CallbackVerificationResult verifyCallback(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return new CallbackVerificationResult(false, "", "", 0, "99", "Empty callback", false, Map.of());
        }

        String receivedSecureHash = queryParams.get("vnp_SecureHash");
        if (receivedSecureHash == null || receivedSecureHash.isBlank()) {
            return new CallbackVerificationResult(false, "", "", 0, "97", "Missing SecureHash", false, queryParams);
        }

        // Filter out secure hash params and sort
        Map<String, String> fields = new TreeMap<>();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isEmpty()
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)) {
                fields.put(key, value);
            }
        }

        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String encodedKey = encode(entry.getKey());
            String encodedValue = encode(entry.getValue());
            hashData.append(encodedKey).append('=').append(encodedValue).append('&');
        }
        if (!hashData.isEmpty()) {
            hashData.setLength(hashData.length() - 1);
        }

        String calculatedHash = hmacSha512(hashSecret, hashData.toString());
        boolean isValidSignature = MessageDigest.isEqual(
            calculatedHash.toLowerCase().getBytes(StandardCharsets.UTF_8),
            receivedSecureHash.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );

        String providerTxRef = queryParams.getOrDefault("vnp_TransactionNo", "");
        String merchantRef = queryParams.getOrDefault("vnp_TxnRef", "");
        String responseCode = queryParams.getOrDefault("vnp_ResponseCode", "");
        String amountStr = queryParams.getOrDefault("vnp_Amount", "0");
        long amountVnd = 0;
        try {
            amountVnd = Long.parseLong(amountStr) / 100L;
        } catch (NumberFormatException ignored) {}

        boolean isSuccess = isValidSignature && "00".equals(responseCode);

        return new CallbackVerificationResult(
            isValidSignature,
            providerTxRef,
            merchantRef,
            amountVnd,
            responseCode,
            isSuccess ? "Thanh toán thành công" : "Giao dịch không thành công hoặc bị hủy",
            isSuccess,
            queryParams
        );
    }

    public static String hmacSha512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA512", ex);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.US_ASCII.toString());
        } catch (Exception ex) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
