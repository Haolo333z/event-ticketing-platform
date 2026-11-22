package com.haolo.ticketing.invoices;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.invoices.EInvoiceModels.*;
import com.haolo.ticketing.shared.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EInvoiceServiceTest {

    private JdbcTemplate jdbc;
    private EInvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        invoiceService = new EInvoiceService(jdbc);
    }

    @Test
    void issueEInvoiceComputesVatAndPersists() {
        UUID accountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        DemoPrincipal principal = new DemoPrincipal(accountId, "CUSTOMER", List.of("CUSTOMER"));

        Map<String, Object> bookingRow = Map.of(
            "id", bookingId,
            "account_id", accountId,
            "status", "CONFIRMED",
            "total_amount_vnd", 1_100_000L
        );
        when(jdbc.queryForList(contains("FROM ticketing.bookings WHERE id = ?"), eq(bookingId)))
            .thenReturn(List.of(bookingRow));
        when(jdbc.query(contains("FROM ticketing.e_invoices WHERE booking_id = ?"), any(RowMapper.class), eq(bookingId)))
            .thenReturn(Collections.emptyList());

        CreateEInvoiceRequest req = new CreateEInvoiceRequest(
            "0101234567",
            "Công ty TNHH Sự Kiện & Giải Trí Sao Kim",
            "Tầng 12, Tòa nhà Landmark, Ba Đình, Hà Nội",
            "finance@saokim.vn"
        );

        // When retrieving after insert
        UUID invoiceId = UUID.randomUUID();
        EInvoiceDto generated = new EInvoiceDto(
            invoiceId, bookingId, accountId, "0101234567",
            "Công ty TNHH Sự Kiện & Giải Trí Sao Kim",
            "Tầng 12, Tòa nhà Landmark, Ba Đình, Hà Nội",
            "finance@saokim.vn", "HDDT-2026-ABCDEF12",
            1_100_000L, 10.0, 100_000L, "ISSUED", Instant.now()
        );
        when(jdbc.query(contains("FROM ticketing.e_invoices WHERE id = ?"), any(RowMapper.class), any(UUID.class)))
            .thenReturn(List.of(generated));

        EInvoiceDto result = invoiceService.issueEInvoice(principal, bookingId, req);
        assertNotNull(result);
        assertEquals(1_100_000L, result.totalAmountVnd());
        assertEquals(100_000L, result.vatAmountVnd());
        assertEquals(10.0, result.vatRate());
        assertEquals("0101234567", result.taxCode());

        verify(jdbc).update(contains("INSERT INTO ticketing.e_invoices"), any(), eq(bookingId), eq(accountId),
            eq("0101234567"), eq("Công ty TNHH Sự Kiện & Giải Trí Sao Kim"),
            eq("Tầng 12, Tòa nhà Landmark, Ba Đình, Hà Nội"), eq("finance@saokim.vn"),
            anyString(), eq(1_100_000L), eq(10.00), eq(100_000L));

        verify(jdbc).update(contains("INSERT INTO ticketing.email_notifications"), eq(bookingId),
            eq("finance@saokim.vn"), anyString(), anyString());
    }

    @Test
    void issueEInvoiceFailsWhenBookingNotConfirmed() {
        UUID accountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        DemoPrincipal principal = new DemoPrincipal(accountId, "CUSTOMER", List.of("CUSTOMER"));

        Map<String, Object> bookingRow = Map.of(
            "id", bookingId,
            "account_id", accountId,
            "status", "PENDING_PAYMENT",
            "total_amount_vnd", 500_000L
        );
        when(jdbc.queryForList(contains("FROM ticketing.bookings WHERE id = ?"), eq(bookingId)))
            .thenReturn(List.of(bookingRow));

        CreateEInvoiceRequest req = new CreateEInvoiceRequest(
            "0101234567", "Công ty CP", "Hà Nội", "acc@test.com"
        );

        ApiException ex = assertThrows(ApiException.class, () -> invoiceService.issueEInvoice(principal, bookingId, req));
        assertEquals(400, ex.status);
        assertEquals("BOOKING_NOT_CONFIRMED", ex.code);
    }

    @Test
    void issueEInvoiceFailsOnIdor() {
        UUID accountId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        DemoPrincipal principal = new DemoPrincipal(accountId, "CUSTOMER", List.of("CUSTOMER"));

        Map<String, Object> bookingRow = Map.of(
            "id", bookingId,
            "account_id", otherAccountId,
            "status", "CONFIRMED",
            "total_amount_vnd", 500_000L
        );
        when(jdbc.queryForList(contains("FROM ticketing.bookings WHERE id = ?"), eq(bookingId)))
            .thenReturn(List.of(bookingRow));

        CreateEInvoiceRequest req = new CreateEInvoiceRequest(
            "0101234567", "Công ty CP", "Hà Nội", "acc@test.com"
        );

        ApiException ex = assertThrows(ApiException.class, () -> invoiceService.issueEInvoice(principal, bookingId, req));
        assertEquals(403, ex.status);
        assertEquals("FORBIDDEN", ex.code);
    }

    @Test
    void lookupEInvoicePublicSuccess() {
        String lookupCode = "HDDT-2026-99ABC123";
        EInvoiceDto invoice = new EInvoiceDto(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "0101234567",
            "Công ty Sao Kim", "Hà Nội", "contact@saokim.vn", lookupCode,
            550_000L, 10.0, 50_000L, "ISSUED", Instant.now()
        );

        when(jdbc.query(contains("FROM ticketing.e_invoices WHERE invoice_lookup_code = ?"), any(RowMapper.class), eq(lookupCode)))
            .thenReturn(List.of(invoice));

        EInvoiceLookupResponse resp = invoiceService.lookupEInvoice(lookupCode);
        assertTrue(resp.found());
        assertNotNull(resp.invoice());
        assertEquals(lookupCode, resp.invoice().invoiceLookupCode());
    }
}
