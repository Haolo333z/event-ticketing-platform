package com.haolo.ticketing.admin;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.admin.AdminModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminRefundServiceTest {

    private JdbcTemplate jdbc;
    private AdminRefundService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final DemoPrincipal adminPrincipal = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));
    private final DemoPrincipal customerPrincipal = new DemoPrincipal(UUID.randomUUID(), "CUSTOMER", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new AdminRefundService(jdbc);
    }

    @Test
    void nonAdminCannotProcessRefund() {
        ApiException ex = assertThrows(ApiException.class, () ->
            service.processRefund(refundId, new ProcessRefundRequest("APPROVE", "Duyệt"), customerPrincipal)
        );
        assertEquals(403, ex.status);
    }

    @Test
    void refundNotFoundThrows404() {
        when(jdbc.queryForList(contains("SELECT r.id, r.booking_id"), eq(refundId)))
            .thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () ->
            service.processRefund(refundId, new ProcessRefundRequest("APPROVE", "Duyệt"), adminPrincipal)
        );
        assertEquals(404, ex.status);
    }

    @Test
    void alreadyCompletedRefundThrows400() {
        when(jdbc.queryForList(contains("SELECT r.id, r.booking_id"), eq(refundId)))
            .thenReturn(List.of(Map.of(
                "id", refundId,
                "booking_id", bookingId,
                "amount_vnd", 500000L,
                "status", "COMPLETED",
                "email", "cust@test.com",
                "display_name", "Khách A"
            )));

        ApiException ex = assertThrows(ApiException.class, () ->
            service.processRefund(refundId, new ProcessRefundRequest("APPROVE", "Duyệt"), adminPrincipal)
        );
        assertEquals(400, ex.status);
        assertEquals("ALREADY_COMPLETED", ex.code);
    }

    @Test
    void approveRefundSuccessLogsEmailAndAudit() {
        when(jdbc.queryForList(contains("SELECT r.id, r.booking_id"), eq(refundId)))
            .thenReturn(List.of(Map.of(
                "id", refundId,
                "booking_id", bookingId,
                "amount_vnd", 750000L,
                "status", "PENDING",
                "email", "cust@test.com",
                "display_name", "Khách A"
            )));

        ProcessRefundResult res = service.processRefund(
            refundId, new ProcessRefundRequest("APPROVE", "Đủ điều kiện hoàn tiền"), adminPrincipal
        );

        assertNotNull(res);
        assertEquals(refundId, res.refundId());
        assertEquals("COMPLETED", res.status());
        assertTrue(res.message().contains("750000 VND"));

        // Verify status update in refund_attempts
        verify(jdbc, times(1)).update(contains("UPDATE ticketing.refund_attempts SET status = ?"), eq("COMPLETED"), eq(refundId));
        // Verify email_notifications logged
        verify(jdbc, times(1)).update(contains("INSERT INTO ticketing.email_notifications"), eq(bookingId), eq("cust@test.com"), anyString(), anyString());
        // Verify audit_logs logged
        verify(jdbc, times(1)).update(contains("INSERT INTO ticketing.audit_logs"), eq(adminId), eq(refundId.toString()), anyString());
    }
}
