package com.haolo.ticketing.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.payments.reconciliation.*;
import com.haolo.ticketing.payments.reconciliation.ReconciliationModels.*;
import com.haolo.ticketing.payments.resilience.CircuitBreakerStatusDto;
import com.haolo.ticketing.payments.resilience.PaymentCircuitBreaker;
import com.haolo.ticketing.payments.resilience.PaymentCircuitBreakerController;
import com.haolo.ticketing.shared.ApiException;

class ReconciliationControllerTest {

    private ReconciliationService reconciliationService;
    private ReconciliationController reconciliationController;

    private PaymentCircuitBreaker circuitBreaker;
    private PaymentCircuitBreakerController circuitBreakerController;

    private final DemoPrincipal adminPrincipal = new DemoPrincipal(UUID.randomUUID(), "ADMIN", List.of("ADMIN"));
    private final DemoPrincipal customerPrincipal = new DemoPrincipal(UUID.randomUUID(), "CUSTOMER", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        reconciliationService = mock(ReconciliationService.class);
        reconciliationController = new ReconciliationController(reconciliationService);

        circuitBreaker = mock(PaymentCircuitBreaker.class);
        circuitBreakerController = new PaymentCircuitBreakerController(circuitBreaker);
    }

    @Test
    void triggerReconciliationAsAdminSucceeds() {
        UUID runId = UUID.randomUUID();
        ReconciliationRunSummary summary = new ReconciliationRunSummary(
            runId, LocalDate.now(), "VNPAY", 2, 2, 0, 1000000L, 1000000L, 0L,
            "COMPLETED", "ADMIN:Test", Instant.now(), Instant.now(), "All clean"
        );
        when(reconciliationService.runReconciliation(any(), anyString(), anyString()))
            .thenReturn(new ReconciliationRunDetail(summary, List.of()));

        ResponseEntity<ReconciliationRunDetail> resp = reconciliationController.triggerReconciliation(
            adminPrincipal, new RunReconciliationRequest("2026-11-21", "CLEAN")
        );

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(runId, resp.getBody().summary().id());
        assertEquals("COMPLETED", resp.getBody().summary().status());
    }

    @Test
    void triggerReconciliationAsCustomerThrowsForbidden() {
        assertThrows(ApiException.class, () ->
            reconciliationController.triggerReconciliation(customerPrincipal, null)
        );
    }

    @Test
    void exportCsvReturnsAttachment() {
        UUID runId = UUID.randomUUID();
        when(reconciliationService.exportReconciliationCsv(runId)).thenReturn("\uFEFFcol1,col2\nval1,val2");

        ResponseEntity<byte[]> resp = reconciliationController.exportCsv(adminPrincipal, runId);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Disposition").contains("eventflow-reconciliation-"));
        assertTrue(new String(resp.getBody()).contains("val1,val2"));
    }

    @Test
    void circuitBreakerControllerGetAndReset() {
        CircuitBreakerStatusDto status = new CircuitBreakerStatusDto(
            "VNPAY", "CLOSED", 0, 3, 30, 0, null, Instant.now()
        );
        when(circuitBreaker.getStatus()).thenReturn(status);

        ResponseEntity<CircuitBreakerStatusDto> getResp = circuitBreakerController.getStatus(adminPrincipal);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertEquals("CLOSED", getResp.getBody().state());

        ResponseEntity<CircuitBreakerStatusDto> resetResp = circuitBreakerController.reset(adminPrincipal);
        assertEquals(HttpStatus.OK, resetResp.getStatusCode());
        verify(circuitBreaker, times(1)).reset();
    }

    @Test
    void circuitBreakerControllerAsCustomerThrowsForbidden() {
        assertThrows(ApiException.class, () ->
            circuitBreakerController.getStatus(customerPrincipal)
        );
    }
}
