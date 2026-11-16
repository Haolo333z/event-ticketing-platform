package com.haolo.ticketing.payments;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.bookings.BookingLifecycle;
import com.haolo.ticketing.bookings.BookingTransactions;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VnPayIpnIntegrationTest {

    private JdbcTemplate jdbc;
    private BookingTransactions transactions;
    private BookingLifecycle lifecycle;
    private PaymentGatewayRegistry gatewayRegistry;
    private VnPayGatewayAdapter vnPayAdapter;
    private PaymentResultProcessor processor;
    private ObjectMapper mapper;

    private PaymentGatewayService service;

    private final String tmnCode = "DEMO_TMN";
    private final String hashSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        transactions = mock(BookingTransactions.class);
        lifecycle = mock(BookingLifecycle.class);
        gatewayRegistry = mock(PaymentGatewayRegistry.class);
        vnPayAdapter = new VnPayGatewayAdapter(tmnCode, hashSecret, "https://pay.url", "http://return.url");
        processor = mock(PaymentResultProcessor.class);
        mapper = new ObjectMapper();

        // transactions.run invokes lambda immediately
        when(transactions.run(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> s = invocation.getArgument(0);
            return s.get();
        });

        service = new PaymentGatewayService(
            jdbc, transactions, lifecycle, gatewayRegistry, vnPayAdapter, processor, mapper
        );
    }

    private Map<String, String> createSignedParams(String merchantRef, long amountVnd, String responseCode, String txNo) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amountVnd * 100L));
        params.put("vnp_TxnRef", merchantRef);
        params.put("vnp_TransactionNo", txNo);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_PayDate", "20261116120000");

        StringBuilder sb = new StringBuilder();
        new TreeMap<>(params).forEach((k, v) -> sb.append(k).append('=').append(v).append('&'));
        sb.setLength(sb.length() - 1);
        String hash = VnPayGatewayAdapter.hmacSha512(hashSecret, sb.toString());
        params.put("vnp_SecureHash", hash);
        return params;
    }

    @Test
    void testIpnInvalidChecksumReturns97() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "EVF_INVALID_HASH");
        params.put("vnp_SecureHash", "wrong-hash-code-123456");

        Map<String, String> response = service.processVnPayIpn(params);
        assertEquals("97", response.get("RspCode"));
        assertEquals("Invalid Checksum", response.get("Message"));
    }

    @Test
    void testIpnOrderNotFoundReturns01() {
        String merchantRef = "EVF_NOT_FOUND";
        Map<String, String> params = createSignedParams(merchantRef, 500000L, "00", "TX123");

        when(jdbc.queryForList(contains("SELECT id, booking_id, amount_vnd, status FROM ticketing.payment_attempts"), eq(merchantRef)))
            .thenReturn(Collections.emptyList());

        Map<String, String> response = service.processVnPayIpn(params);
        assertEquals("01", response.get("RspCode"));
        assertEquals("Order not found", response.get("Message"));
    }

    @Test
    void testIpnAmountMismatchReturns04() {
        String merchantRef = "EVF_MISMATCH";
        Map<String, String> params = createSignedParams(merchantRef, 300000L, "00", "TX123");

        UUID attemptId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(jdbc.queryForList(contains("SELECT id, booking_id, amount_vnd, status FROM ticketing.payment_attempts"), eq(merchantRef)))
            .thenReturn(List.of(
                Map.of("id", attemptId, "booking_id", bookingId, "amount_vnd", 500000L, "status", "INITIATED")
            ));

        Map<String, String> response = service.processVnPayIpn(params);
        assertEquals("04", response.get("RspCode"));
        assertEquals("Invalid Amount", response.get("Message"));
    }

    @Test
    void testIpnDuplicateAlreadyProcessedReturns02() {
        String merchantRef = "EVF_DUP";
        String txNo = "TX_DUP_001";
        Map<String, String> params = createSignedParams(merchantRef, 500000L, "00", txNo);

        UUID attemptId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(jdbc.queryForList(contains("SELECT id, booking_id, amount_vnd, status FROM ticketing.payment_attempts"), eq(merchantRef)))
            .thenReturn(List.of(
                Map.of("id", attemptId, "booking_id", bookingId, "amount_vnd", 500000L, "status", "INITIATED")
            ));

        when(jdbc.queryForList(contains("SELECT id, processed FROM ticketing.payment_notifications"), eq(txNo), eq("PAYMENT_SUCCESS")))
            .thenReturn(List.of(
                Map.of("id", UUID.randomUUID(), "processed", true)
            ));

        Map<String, String> response = service.processVnPayIpn(params);
        assertEquals("02", response.get("RspCode"));
        assertEquals("Order already confirmed", response.get("Message"));
    }

    @Test
    void testIpnSuccessTimelyReturns00() {
        String merchantRef = "EVF_SUCCESS";
        String txNo = "TX_OK_001";
        Map<String, String> params = createSignedParams(merchantRef, 500000L, "00", txNo);

        UUID attemptId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(jdbc.queryForList(contains("SELECT id, booking_id, amount_vnd, status FROM ticketing.payment_attempts"), eq(merchantRef)))
            .thenReturn(List.of(
                Map.of("id", attemptId, "booking_id", bookingId, "amount_vnd", 500000L, "status", "INITIATED")
            ));

        when(jdbc.queryForList(contains("SELECT id, processed FROM ticketing.payment_notifications"), eq(txNo), eq("PAYMENT_SUCCESS")))
            .thenReturn(Collections.emptyList());

        when(jdbc.queryForObject(contains("SELECT status FROM ticketing.bookings"), eq(String.class), eq(bookingId)))
            .thenReturn("CONFIRMED");

        Map<String, String> response = service.processVnPayIpn(params);
        assertEquals("00", response.get("RspCode"));
        assertEquals("Confirm Success", response.get("Message"));

        verify(processor, times(1)).process(eq(bookingId), any(UUID.class));
    }

    @Test
    void testIpnLateSuccessTriggersRefundAttempt() {
        String merchantRef = "EVF_LATE_SUCCESS";
        String txNo = "TX_LATE_001";
        Map<String, String> params = createSignedParams(merchantRef, 500000L, "00", txNo);

        UUID attemptId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(jdbc.queryForList(contains("SELECT id, booking_id, amount_vnd, status FROM ticketing.payment_attempts"), eq(merchantRef)))
            .thenReturn(List.of(
                Map.of("id", attemptId, "booking_id", bookingId, "amount_vnd", 500000L, "status", "INITIATED")
            ));

        when(jdbc.queryForList(contains("SELECT id, processed FROM ticketing.payment_notifications"), eq(txNo), eq("PAYMENT_SUCCESS")))
            .thenReturn(Collections.emptyList());

        // Processor handles late payment and updates status to REFUND_REQUIRED
        when(jdbc.queryForObject(contains("SELECT status FROM ticketing.bookings"), eq(String.class), eq(bookingId)))
            .thenReturn("REFUND_REQUIRED");

        Map<String, String> response = service.processVnPayIpn(params);
        assertEquals("00", response.get("RspCode"));
        assertEquals("Confirm Success", response.get("Message"));

        verify(processor, times(1)).process(eq(bookingId), any(UUID.class));

        // Verify refund_attempts insertion
        verify(jdbc, times(1)).update(
            contains("INSERT INTO ticketing.refund_attempts"),
            any(UUID.class), eq(bookingId), eq(attemptId), eq(500000L)
        );
    }
}
