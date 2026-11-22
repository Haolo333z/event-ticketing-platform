package com.haolo.ticketing.commercial;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.invoices.*;
import com.haolo.ticketing.invoices.EInvoiceModels.*;
import com.haolo.ticketing.organizer.OrganizerOnboardingController;
import com.haolo.ticketing.organizer.OrganizerOnboardingController.*;
import com.haolo.ticketing.organizer.payouts.*;
import com.haolo.ticketing.organizer.payouts.PayoutModels.*;
import com.haolo.ticketing.vouchers.*;
import com.haolo.ticketing.vouchers.VoucherModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommercialControllersTest {

    private VoucherService voucherService;
    private VoucherController voucherController;

    private EInvoiceService invoiceService;
    private EInvoiceController invoiceController;

    private OrganizerPayoutService payoutService;
    private OrganizerPayoutController payoutController;

    private JdbcTemplate jdbc;
    private OrganizerOnboardingController onboardingController;

    private final DemoPrincipal customer = new DemoPrincipal(UUID.randomUUID(), "CUSTOMER", List.of("CUSTOMER"));
    private final DemoPrincipal organizer = new DemoPrincipal(UUID.randomUUID(), "ORGANIZER", List.of("ORGANIZER"));
    private final DemoPrincipal admin = new DemoPrincipal(UUID.randomUUID(), "ADMIN", List.of("ADMIN"));

    @BeforeEach
    void setUp() {
        voucherService = mock(VoucherService.class);
        voucherController = new VoucherController(voucherService);

        invoiceService = mock(EInvoiceService.class);
        invoiceController = new EInvoiceController(invoiceService);

        payoutService = mock(OrganizerPayoutService.class);
        payoutController = new OrganizerPayoutController(payoutService);

        jdbc = mock(JdbcTemplate.class);
        onboardingController = new OrganizerOnboardingController(jdbc);
    }

    @Test
    void validateVoucherControllerEndpoint() {
        ValidateVoucherRequest req = new ValidateVoucherRequest("SUMMER2026", null, null, 500_000L);
        VoucherValidationResult mockResult = new VoucherValidationResult(
            true, UUID.randomUUID(), "SUMMER2026", "PERCENTAGE", 10, 50_000L, 450_000L, "Thành công"
        );
        when(voucherService.validateAndCalculate(eq(customer.accountId()), eq("SUMMER2026"), isNull(), isNull(), eq(500_000L)))
            .thenReturn(mockResult);

        ResponseEntity<VoucherValidationResult> resp = voucherController.validateVoucher(req, customer);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().valid());
        assertEquals(50_000L, resp.getBody().discountAmountVnd());
    }

    @Test
    void applyVoucherControllerEndpoint() {
        UUID bookingId = UUID.randomUUID();
        ApplyVoucherRequest req = new ApplyVoucherRequest("SUMMER2026");
        ApplyVoucherResponse mockResp = new ApplyVoucherResponse(
            bookingId, UUID.randomUUID(), "SUMMER2026", 500_000L, 50_000L, 450_000L, "Thành công"
        );
        when(voucherService.applyVoucherToBooking(eq(customer.accountId()), eq(bookingId), eq("SUMMER2026")))
            .thenReturn(mockResp);

        ResponseEntity<ApplyVoucherResponse> resp = voucherController.applyVoucher(bookingId, req, customer);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(450_000L, resp.getBody().finalAmountVnd());
    }

    @Test
    void issueEInvoiceControllerEndpoint() {
        UUID bookingId = UUID.randomUUID();
        CreateEInvoiceRequest req = new CreateEInvoiceRequest("0101234567", "Công ty A", "Hà Nội", "email@a.com");
        EInvoiceDto mockDto = new EInvoiceDto(
            UUID.randomUUID(), bookingId, customer.accountId(), "0101234567", "Công ty A", "Hà Nội", "email@a.com",
            "HDDT-2026-XYZ12345", 550_000L, 10.0, 50_000L, "ISSUED", Instant.now()
        );
        when(invoiceService.issueEInvoice(eq(customer), eq(bookingId), eq(req)))
            .thenReturn(mockDto);

        ResponseEntity<EInvoiceDto> resp = invoiceController.issueEInvoice(bookingId, req, customer);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("HDDT-2026-XYZ12345", resp.getBody().invoiceLookupCode());
    }

    @Test
    void requestPayoutControllerEndpoint() {
        UUID eventId = UUID.randomUUID();
        RequestPayoutRequest req = new RequestPayoutRequest(eventId, 5_000_000L, "Vietcombank", "0011002233", "LE HUU HAO");
        OrganizerPayoutDto mockDto = new OrganizerPayoutDto(
            UUID.randomUUID(), UUID.randomUUID(), "Org Name", eventId, "Event Title",
            5_263_158L, 5.0, 263_158L, 5_000_000L, "Vietcombank", "0011002233", "LE HUU HAO",
            "REQUESTED", Instant.now(), null, null
        );
        when(payoutService.requestPayout(eq(organizer), eq(req))).thenReturn(mockDto);

        ResponseEntity<OrganizerPayoutDto> resp = payoutController.requestPayout(req, organizer);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("REQUESTED", resp.getBody().status());
        assertEquals(5_000_000L, resp.getBody().netPayoutVnd());
    }

    @Test
    void submitOnboardRequestEndpoint() {
        OrganizerOnboardRequest req = new OrganizerOnboardRequest(
            "Công ty CP Giải trí StarLight", "contact@starlight.vn", "0901234567", "0314567890", "PROFESSIONAL", "Đăng ký đối tác"
        );
        when(jdbc.update(contains("INSERT INTO ticketing.contact_leads"), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(1);

        ResponseEntity<OrganizerOnboardResponse> resp = onboardingController.submitOnboardRequest(req);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("Công ty CP Giải trí StarLight", resp.getBody().organizationName());
        assertEquals("PROFESSIONAL", resp.getBody().saasPlan());
    }
}
