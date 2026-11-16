package com.haolo.ticketing.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.payments.PaymentModels.*;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentCheckoutControllerTest {

    private PaymentGatewayService service;
    private VnPayGatewayAdapter vnPayAdapter;
    private PaymentGatewayController controller;

    private final UUID customerId = UUID.randomUUID();
    private final DemoPrincipal customer = new DemoPrincipal(customerId, "CUSTOMER", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        service = Mockito.mock(PaymentGatewayService.class);
        vnPayAdapter = Mockito.mock(VnPayGatewayAdapter.class);
        controller = new PaymentGatewayController(service, vnPayAdapter);
    }

    @Test
    void testCheckoutSuccess() {
        UUID bookingId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        CheckoutRequest req = new CheckoutRequest(bookingId, "VNPAY", "http://localhost:5173/payment-return");

        PaymentInitResult initResult = new PaymentInitResult(
            attemptId, "EVF_REF_01", 500000L, "VND", "https://sandbox.vnpayment.vn/test-pay-url", Instant.now().plusSeconds(600).toString()
        );

        when(service.checkout(eq(customer), eq(req), anyString())).thenReturn(initResult);

        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        httpReq.setRemoteAddr("192.168.1.100");

        ResponseEntity<PaymentInitResult> res = controller.checkout(customer, req, httpReq);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("EVF_REF_01", res.getBody().merchantRef());
        assertEquals(500000L, res.getBody().amountVnd());
        assertTrue(res.getBody().gatewayUrl().contains("vnpayment.vn"));
    }

    @Test
    void testCheckoutForbiddenForNonCustomer() {
        UUID bookingId = UUID.randomUUID();
        CheckoutRequest req = new CheckoutRequest(bookingId, "VNPAY", "http://localhost:5173/payment-return");

        when(service.checkout(any(), any(), anyString()))
            .thenThrow(new ApiException(403, "FORBIDDEN", "Chỉ khách hàng mới được thanh toán"));

        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        ApiException ex = assertThrows(ApiException.class, () -> controller.checkout(customer, req, httpReq));
        assertEquals(403, ex.status);
    }

    @Test
    void testGetPaymentStatusEndpoint() {
        UUID bookingId = UUID.randomUUID();
        PaymentStatusResponse statusResponse = new PaymentStatusResponse(
            bookingId, "PENDING_PAYMENT", 500000L, "VNPAY", "EVF_REF_01", "https://pay.url", Instant.now().toString()
        );

        when(service.getPaymentStatus(eq(customer), eq(bookingId))).thenReturn(statusResponse);

        ResponseEntity<PaymentStatusResponse> res = controller.getPaymentStatus(customer, bookingId.toString());
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("PENDING_PAYMENT", res.getBody().status());
        assertEquals(500000L, res.getBody().totalAmountVnd());
    }
}
