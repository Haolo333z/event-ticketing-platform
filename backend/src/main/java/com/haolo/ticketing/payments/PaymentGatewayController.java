package com.haolo.ticketing.payments;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.bookings.GaBookingRequest;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.payments.PaymentModels.*;

@RestController
public class PaymentGatewayController {

    private final PaymentGatewayService paymentGatewayService;
    private final VnPayGatewayAdapter vnPayAdapter;

    public PaymentGatewayController(
            PaymentGatewayService paymentGatewayService,
            VnPayGatewayAdapter vnPayAdapter) {
        this.paymentGatewayService = paymentGatewayService;
        this.vnPayAdapter = vnPayAdapter;
    }

    @PostMapping("/api/payments/checkout")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<PaymentInitResult> checkout(
            @AuthenticationPrincipal DemoPrincipal principal,
            @RequestBody CheckoutRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        PaymentInitResult result = paymentGatewayService.checkout(principal, request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/api/payments/vnpay/return")
    public ResponseEntity<CallbackVerificationResult> vnpayReturn(
            @RequestParam Map<String, String> allParams) {
        CallbackVerificationResult result = vnPayAdapter.verifyCallback(allParams);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/payments/vnpay/ipn")
    public ResponseEntity<Map<String, String>> vnpayIpn(
            @RequestParam Map<String, String> allParams) {
        Map<String, String> ipnResult = paymentGatewayService.processVnPayIpn(allParams);
        return ResponseEntity.ok(ipnResult);
    }

    @GetMapping("/api/bookings/{id}/payment-status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @AuthenticationPrincipal DemoPrincipal principal,
            @PathVariable String id) {
        PaymentStatusResponse response = paymentGatewayService.getPaymentStatus(principal, GaBookingRequest.uuid(id));
        return ResponseEntity.ok(response);
    }

    private static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return (ip != null && !ip.isBlank()) ? ip : "127.0.0.1";
    }
}
