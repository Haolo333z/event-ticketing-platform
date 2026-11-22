package com.haolo.ticketing.vouchers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.EventSecurityService;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.vouchers.VoucherModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoucherServiceTest {

    private JdbcTemplate jdbc;
    private EventSecurityService securityService;
    private VoucherService voucherService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        securityService = mock(EventSecurityService.class);
        voucherService = new VoucherService(jdbc, securityService);
    }

    @Test
    void validateVoucherPercentageDiscountWithCap() {
        UUID voucherId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        VoucherDto voucher = new VoucherDto(
            voucherId, null, null, null, "SUMMER2026", "PERCENTAGE", 10,
            100_000L, 50_000L, 100, 5,
            now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS), true, now
        );

        when(jdbc.query(anyString(), any(RowMapper.class), eq("SUMMER2026")))
            .thenReturn(List.of(voucher));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(voucherId), eq(accountId)))
            .thenReturn(0);

        // Order 800,000 -> 10% is 80,000, capped at 50,000 -> discount 50,000, final 750,000
        VoucherValidationResult result = voucherService.validateAndCalculate(accountId, "SUMMER2026", null, null, 800_000L);
        assertTrue(result.valid());
        assertEquals(50_000L, result.discountAmountVnd());
        assertEquals(750_000L, result.finalAmountVnd());
    }

    @Test
    void validateVoucherFixedDiscount() {
        UUID voucherId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        VoucherDto voucher = new VoucherDto(
            voucherId, null, null, null, "WELCOME50K", "FIXED_VND", 50_000L,
            150_000L, null, 100, 0,
            now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS), true, now
        );

        when(jdbc.query(anyString(), any(RowMapper.class), eq("WELCOME50K")))
            .thenReturn(List.of(voucher));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(voucherId), eq(accountId)))
            .thenReturn(0);

        // Order 200,000 -> discount 50,000 -> final 150,000
        VoucherValidationResult result = voucherService.validateAndCalculate(accountId, "WELCOME50K", null, null, 200_000L);
        assertTrue(result.valid());
        assertEquals(50_000L, result.discountAmountVnd());
        assertEquals(150_000L, result.finalAmountVnd());
    }

    @Test
    void validateVoucherFailsWhenExpired() {
        UUID voucherId = UUID.randomUUID();
        Instant now = Instant.now();

        VoucherDto voucher = new VoucherDto(
            voucherId, null, null, null, "EXPIRED10", "PERCENTAGE", 10,
            0L, null, 100, 0,
            now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), true, now
        );

        when(jdbc.query(anyString(), any(RowMapper.class), eq("EXPIRED10")))
            .thenReturn(List.of(voucher));

        VoucherValidationResult result = voucherService.validateAndCalculate(null, "EXPIRED10", null, null, 200_000L);
        assertFalse(result.valid());
        assertTrue(result.message().contains("hết hạn"));
    }

    @Test
    void validateVoucherFailsWhenMaxUsesReached() {
        UUID voucherId = UUID.randomUUID();
        Instant now = Instant.now();

        VoucherDto voucher = new VoucherDto(
            voucherId, null, null, null, "MAXEDOUT", "FIXED_VND", 20_000L,
            0L, null, 10, 10,
            now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), true, now
        );

        when(jdbc.query(anyString(), any(RowMapper.class), eq("MAXEDOUT")))
            .thenReturn(List.of(voucher));

        VoucherValidationResult result = voucherService.validateAndCalculate(null, "MAXEDOUT", null, null, 200_000L);
        assertFalse(result.valid());
        assertTrue(result.message().contains("hết lượt"));
    }

    @Test
    void validateVoucherFailsWhenAlreadyRedeemedByUser() {
        UUID voucherId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        VoucherDto voucher = new VoucherDto(
            voucherId, null, null, null, "ONEUSER", "FIXED_VND", 20_000L,
            0L, null, 100, 5,
            now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), true, now
        );

        when(jdbc.query(anyString(), any(RowMapper.class), eq("ONEUSER")))
            .thenReturn(List.of(voucher));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(voucherId), eq(accountId)))
            .thenReturn(1);

        VoucherValidationResult result = voucherService.validateAndCalculate(accountId, "ONEUSER", null, null, 200_000L);
        assertFalse(result.valid());
        assertTrue(result.message().contains("đã sử dụng"));
    }

    @Test
    void applyVoucherToBookingSuccessful() {
        UUID accountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        Instant now = Instant.now();

        Map<String, Object> bookingRow = new HashMap<>();
        bookingRow.put("id", bookingId);
        bookingRow.put("account_id", accountId);
        bookingRow.put("event_id", UUID.randomUUID());
        bookingRow.put("ticket_type_id", UUID.randomUUID());
        bookingRow.put("status", "PENDING_PAYMENT");
        bookingRow.put("total_amount_vnd", 300_000L);
        bookingRow.put("voucher_id", null);
        bookingRow.put("discount_amount_vnd", 0L);

        when(jdbc.queryForList(contains("FROM ticketing.bookings WHERE id = ? FOR UPDATE"), eq(bookingId)))
            .thenReturn(List.of(bookingRow));

        VoucherDto voucher = new VoucherDto(
            voucherId, null, null, null, "SALE50K", "FIXED_VND", 50_000L,
            100_000L, null, 100, 1,
            now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), true, now
        );

        when(jdbc.query(contains("FROM ticketing.vouchers WHERE code = ? FOR UPDATE"), any(RowMapper.class), eq("SALE50K")))
            .thenReturn(List.of(voucher));
        when(jdbc.query(contains("FROM ticketing.vouchers WHERE code = ?"), any(RowMapper.class), eq("SALE50K")))
            .thenReturn(List.of(voucher));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(voucherId), eq(accountId)))
            .thenReturn(0);

        ApplyVoucherResponse resp = voucherService.applyVoucherToBooking(accountId, bookingId, "SALE50K");
        assertEquals(bookingId, resp.bookingId());
        assertEquals(300_000L, resp.originalAmountVnd());
        assertEquals(50_000L, resp.discountAmountVnd());
        assertEquals(250_000L, resp.finalAmountVnd());

        verify(jdbc).update(contains("UPDATE ticketing.vouchers SET used_count = used_count + 1"), eq(voucherId));
        verify(jdbc).update(contains("UPDATE ticketing.bookings SET voucher_id = ?"), eq(voucherId), eq(50_000L), eq(250_000L), eq(bookingId));
        verify(jdbc).update(contains("INSERT INTO ticketing.voucher_redemptions"), eq(voucherId), eq(bookingId), eq(accountId), eq(50_000L));
    }

    @Test
    void releaseVoucherForBookingRestoresUsage() {
        UUID bookingId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();

        Map<String, Object> bookingRow = Map.of(
            "voucher_id", voucherId,
            "discount_amount_vnd", 50_000L,
            "total_amount_vnd", 200_000L
        );
        when(jdbc.queryForList(contains("SELECT voucher_id, discount_amount_vnd"), eq(bookingId)))
            .thenReturn(List.of(bookingRow));

        voucherService.releaseVoucherForBooking(bookingId);

        verify(jdbc).update(contains("UPDATE ticketing.vouchers SET used_count = GREATEST(0, used_count - 1)"), eq(voucherId));
        verify(jdbc).update(contains("DELETE FROM ticketing.voucher_redemptions WHERE booking_id = ?"), eq(bookingId));
        verify(jdbc).update(contains("UPDATE ticketing.bookings SET voucher_id = NULL"), eq(50_000L), eq(bookingId));
    }
}
