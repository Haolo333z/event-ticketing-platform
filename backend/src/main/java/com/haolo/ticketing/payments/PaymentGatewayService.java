package com.haolo.ticketing.payments;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.bookings.BookingLifecycle;
import com.haolo.ticketing.bookings.BookingTransactions;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.payments.PaymentModels.*;
import com.haolo.ticketing.shared.ApiException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayService.class);

    private final JdbcTemplate jdbc;
    private final BookingTransactions transactions;
    private final BookingLifecycle lifecycle;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final VnPayGatewayAdapter vnPayAdapter;
    private final PaymentResultProcessor paymentResultProcessor;
    private final ObjectMapper mapper;

    public PaymentGatewayService(
            JdbcTemplate jdbc,
            BookingTransactions transactions,
            BookingLifecycle lifecycle,
            PaymentGatewayRegistry gatewayRegistry,
            VnPayGatewayAdapter vnPayAdapter,
            PaymentResultProcessor paymentResultProcessor,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.lifecycle = lifecycle;
        this.gatewayRegistry = gatewayRegistry;
        this.vnPayAdapter = vnPayAdapter;
        this.paymentResultProcessor = paymentResultProcessor;
        this.mapper = mapper;
    }

    public PaymentInitResult checkout(DemoPrincipal principal, CheckoutRequest request, String ipAddr) {
        if (principal == null || (!principal.hasRole("CUSTOMER") && !principal.hasRole("ADMIN"))) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ khách hàng có tài khoản mới được khởi tạo thanh toán");
        }
        if (request == null || request.bookingId() == null) {
            throw ApiException.invalid();
        }

        return transactions.run(() -> {
            var booking = lifecycle.lock(request.bookingId());
            UUID accountId = (UUID) booking.get("account_id");
            if (!principal.hasRole("ADMIN") && !principal.accountId().equals(accountId)) {
                throw new ApiException(403, "FORBIDDEN", "Bạn không có quyền thanh toán cho đơn hàng này");
            }

            String status = (String) booking.get("status");
            if (!"PENDING_PAYMENT".equals(status)) {
                throw new ApiException(400, "INVALID_BOOKING_STATE", "Đơn hàng không ở trạng thái chờ thanh toán (hiện tại: " + status + ")");
            }

            Timestamp expiresAt = (Timestamp) booking.get("expires_at");
            if (expiresAt.toInstant().isBefore(Instant.now())) {
                throw new ApiException(400, "BOOKING_HOLD_EXPIRED", "Đơn hàng đã hết thời gian giữ chỗ");
            }

            long totalAmountVnd = ((Number) booking.get("total_amount_vnd")).longValue();
            String provider = request.provider() != null && !request.provider().isBlank()
                    ? request.provider().toUpperCase()
                    : "VNPAY";

            PaymentGatewayPort port = gatewayRegistry.get(provider);
            UUID attemptId = UUID.randomUUID();
            String merchantRef = "EVF_" + request.bookingId().toString().substring(0, 8) + "_" + System.currentTimeMillis();

            PaymentInitCommand cmd = new PaymentInitCommand(
                request.bookingId(),
                port.getProvider(),
                totalAmountVnd,
                "Thanh toan ve EventFlow " + merchantRef,
                ipAddr,
                request.returnUrl(),
                merchantRef,
                expiresAt.toInstant()
            );

            PaymentInitResult result = port.initiateCheckout(cmd, attemptId);

            jdbc.update(
                "INSERT INTO ticketing.payment_attempts(id, booking_id, provider, merchant_ref, amount_vnd, currency, status, gateway_url, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, 'VND', 'INITIATED', ?, ?)",
                attemptId, request.bookingId(), port.getProvider(), merchantRef, totalAmountVnd, result.gatewayUrl(), expiresAt
            );

            return result;
        });
    }

    public Map<String, String> processVnPayIpn(Map<String, String> queryParams) {
        CallbackVerificationResult verification = vnPayAdapter.verifyCallback(queryParams);

        String jsonPayload;
        try {
            jsonPayload = mapper.writeValueAsString(queryParams);
        } catch (Exception ex) {
            jsonPayload = "{}";
        }

        String signature = queryParams.getOrDefault("vnp_SecureHash", "");

        if (!verification.isValidSignature()) {
            log.warn("VNPAY IPN signature verification failed for merchantRef={}", verification.merchantRef());
            recordNotification(null, "VNPAY", verification.providerTxRef(), "PAYMENT_FAILURE", jsonPayload, signature, false, false);
            return Map.of("RspCode", "97", "Message", "Invalid Checksum");
        }

        var attempts = jdbc.queryForList(
            "SELECT id, booking_id, amount_vnd, status FROM ticketing.payment_attempts WHERE merchant_ref = ?",
            verification.merchantRef()
        );

        if (attempts.isEmpty()) {
            log.warn("VNPAY IPN order not found: merchantRef={}", verification.merchantRef());
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        var attempt = attempts.getFirst();
        UUID attemptId = (UUID) attempt.get("id");
        UUID bookingId = (UUID) attempt.get("booking_id");
        long attemptAmountVnd = ((Number) attempt.get("amount_vnd")).longValue();

        if (attemptAmountVnd != verification.amountVnd()) {
            log.warn("VNPAY IPN amount mismatch: expected={} received={}", attemptAmountVnd, verification.amountVnd());
            return Map.of("RspCode", "04", "Message", "Invalid Amount");
        }

        String eventType = verification.isSuccess() ? "PAYMENT_SUCCESS" : "PAYMENT_FAILURE";

        // Idempotency check on notifications
        var existingNotifs = jdbc.queryForList(
            "SELECT id, processed FROM ticketing.payment_notifications WHERE provider = 'VNPAY' AND provider_tx_ref = ? AND event_type = ?",
            verification.providerTxRef(), eventType
        );

        if (!existingNotifs.isEmpty() && Boolean.TRUE.equals(existingNotifs.getFirst().get("processed"))) {
            log.info("VNPAY IPN duplicate received and already processed for providerTxRef={}", verification.providerTxRef());
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }

        // Record notification
        recordNotification(attemptId, "VNPAY", verification.providerTxRef(), eventType, jsonPayload, signature, true, true);

        // Process state transition in business transaction
        transactions.run(() -> {
            UUID paymentId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            if (verification.isSuccess()) {
                jdbc.update(
                    "INSERT INTO ticketing.payments(id, booking_id, request_id, scenario, status, recorded_at, correlation_id) " +
                    "VALUES (?, ?, ?, 'VNPAY_SUCCESS', 'SUCCESS', clock_timestamp(), ?)",
                    paymentId, bookingId, verification.merchantRef(), correlationId
                );
                jdbc.update(
                    "UPDATE ticketing.payment_attempts SET status = 'SUCCESS', provider_tx_ref = ?, updated_at = clock_timestamp() WHERE id = ?",
                    verification.providerTxRef(), attemptId
                );
                paymentResultProcessor.process(bookingId, paymentId);

                // Check if late payment triggered REFUND_REQUIRED
                String currentStatus = jdbc.queryForObject("SELECT status FROM ticketing.bookings WHERE id = ?", String.class, bookingId);
                if ("REFUND_REQUIRED".equals(currentStatus)) {
                    log.info("Late payment confirmed after expiry: creating refund_attempt for bookingId={}", bookingId);
                    jdbc.update(
                        "INSERT INTO ticketing.refund_attempts(id, booking_id, payment_attempt_id, amount_vnd, reason, status) " +
                        "VALUES (?, ?, ?, ?, 'LATE_PAYMENT_AFTER_EXPIRY', 'PENDING')",
                        UUID.randomUUID(), bookingId, attemptId, attemptAmountVnd
                    );
                }
            } else {
                jdbc.update(
                    "INSERT INTO ticketing.payments(id, booking_id, request_id, scenario, status, recorded_at, correlation_id) " +
                    "VALUES (?, ?, ?, 'VNPAY_FAILED', 'FAILURE', clock_timestamp(), ?)",
                    paymentId, bookingId, verification.merchantRef(), correlationId
                );
                jdbc.update(
                    "UPDATE ticketing.payment_attempts SET status = 'FAILED', provider_tx_ref = ?, updated_at = clock_timestamp() WHERE id = ?",
                    verification.providerTxRef(), attemptId
                );
                paymentResultProcessor.process(bookingId, paymentId);
            }
            return null;
        });

        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaymentStatusResponse getPaymentStatus(DemoPrincipal principal, UUID bookingId) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        }

        var rows = jdbc.queryForList(
            "SELECT id, account_id, status, total_amount_vnd, expires_at FROM ticketing.bookings WHERE id = ?",
            bookingId
        );
        if (rows.isEmpty()) {
            throw ApiException.missing();
        }

        var b = rows.getFirst();
        UUID accountId = (UUID) b.get("account_id");
        if (!principal.hasRole("ADMIN") && !principal.accountId().equals(accountId)) {
            throw new ApiException(403, "FORBIDDEN", "Không có quyền xem thông tin đơn hàng này");
        }

        String status = (String) b.get("status");
        long totalAmount = ((Number) b.get("total_amount_vnd")).longValue();
        Timestamp expiresAt = (Timestamp) b.get("expires_at");

        var attemptRows = jdbc.queryForList(
            "SELECT provider, merchant_ref, gateway_url FROM ticketing.payment_attempts WHERE booking_id = ? ORDER BY created_at DESC LIMIT 1",
            bookingId
        );

        String provider = attemptRows.isEmpty() ? null : (String) attemptRows.getFirst().get("provider");
        String merchantRef = attemptRows.isEmpty() ? null : (String) attemptRows.getFirst().get("merchant_ref");
        String gatewayUrl = attemptRows.isEmpty() ? null : (String) attemptRows.getFirst().get("gateway_url");

        return new PaymentStatusResponse(
            bookingId,
            status,
            totalAmount,
            provider,
            merchantRef,
            gatewayUrl,
            expiresAt.toInstant().toString()
        );
    }

    private void recordNotification(
            UUID attemptId, String provider, String providerTxRef, String eventType,
            String rawPayload, String signature, boolean signatureValid, boolean processed) {
        try {
            jdbc.update(
                "INSERT INTO ticketing.payment_notifications(id, attempt_id, provider, provider_tx_ref, event_type, raw_payload, signature, signature_valid, processed, processed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, " + (processed ? "clock_timestamp()" : "NULL") + ") " +
                "ON CONFLICT(provider, provider_tx_ref, event_type) DO NOTHING",
                UUID.randomUUID(), attemptId, provider,
                providerTxRef.isBlank() ? "TX_" + UUID.randomUUID().toString().substring(0, 8) : providerTxRef,
                eventType, rawPayload, signature, signatureValid, processed
            );
        } catch (Exception ex) {
            log.error("Failed to insert payment notification: ", ex);
        }
    }
}
