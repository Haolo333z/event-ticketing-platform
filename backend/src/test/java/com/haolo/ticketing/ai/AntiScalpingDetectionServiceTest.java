package com.haolo.ticketing.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.ai.AiModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@ExtendWith(MockitoExtension.class)
class AntiScalpingDetectionServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private AntiScalpingDetectionService service;

    @BeforeEach
    void setUp() {
        service = new AntiScalpingDetectionService(jdbc);
    }

    @Test
    void testCreateAlert() {
        UUID eventId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();

        when(jdbc.update(anyString(), any(UUID.class), anyString(), any(UUID.class), any(UUID.class), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(1);

        UUID alertId = service.createAlert(
            "MASS_HOLD_SAME_IP",
            eventId,
            targetAccountId,
            "103.20.15.88",
            "HIGH",
            "Cảnh báo gom vé cụm bot IP",
            Map.of("clusterSize", 5)
        );

        assertNotNull(alertId);
        verify(jdbc).update(
            contains("INSERT INTO ticketing.scalping_alerts"),
            eq(alertId),
            eq("MASS_HOLD_SAME_IP"),
            eq(eventId),
            eq(targetAccountId),
            eq("103.20.15.88"),
            eq("HIGH"),
            anyString(),
            eq("Cảnh báo gom vé cụm bot IP")
        );
    }

    @Test
    void testTakeAction_LockAccount() {
        UUID alertId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        when(jdbc.queryForMap(contains("FROM ticketing.scalping_alerts"), eq(alertId)))
            .thenReturn(Map.of("target_account_id", targetAccountId, "event_id", eventId, "status", "NEW"));

        when(jdbc.queryForList(contains("FROM ticketing.bookings WHERE account_id = ? AND status = 'PENDING_PAYMENT'"), eq(targetAccountId)))
            .thenReturn(List.of(Map.of("id", bookingId, "ticket_type_id", ticketTypeId, "event_id", eventId)));

        when(jdbc.queryForObject(contains("FROM ticketing.booking_items"), eq(Integer.class), eq(bookingId)))
            .thenReturn(4);

        DemoPrincipal adminPrincipal = new DemoPrincipal(UUID.randomUUID(), "admin@eventflow.vn", List.of("ROLE_ADMIN"));
        ScalpingActionRequest req = new ScalpingActionRequest("LOCK_ACCOUNT", "Khóa vì vi phạm đầu cơ vé");

        ScalpingActionResponse response = service.takeAction(alertId, req, adminPrincipal);

        assertNotNull(response);
        assertEquals("ACTION_TAKEN", response.status());
        assertEquals("LOCK_ACCOUNT", response.actionTaken());
        assertEquals(1, response.releasedBookingsCount());
        assertTrue(response.messageVi().contains("Đã khóa vĩnh viễn tài khoản"));

        // Verify account locked
        verify(jdbc).update("UPDATE ticketing.users SET status = 'LOCKED' WHERE id = ?", targetAccountId);
        // Verify tokens revoked
        verify(jdbc).update("UPDATE ticketing.refresh_tokens SET is_revoked = true WHERE user_id = ? AND is_revoked = false", targetAccountId);
        // Verify seats released
        verify(jdbc).update(contains("UPDATE ticketing.seats SET status = 'AVAILABLE'"), eq(bookingId));
        // Verify GA inventory released
        verify(jdbc).update(contains("UPDATE ticketing.inventory_ga SET held = GREATEST(0, held - ?)"), eq(4), eq(ticketTypeId));
        // Verify booking cancelled
        verify(jdbc).update(contains("UPDATE ticketing.bookings SET status = 'CANCELLED'"), eq(bookingId));
    }

    @Test
    void testTakeAction_Dismiss() {
        UUID alertId = UUID.randomUUID();
        when(jdbc.queryForMap(contains("FROM ticketing.scalping_alerts"), eq(alertId)))
            .thenReturn(Map.of("status", "NEW"));

        DemoPrincipal adminPrincipal = new DemoPrincipal(UUID.randomUUID(), "admin@eventflow.vn", List.of("ROLE_ADMIN"));
        ScalpingActionRequest req = new ScalpingActionRequest("DISMISS", "Không phải hành vi gian lận");

        ScalpingActionResponse response = service.takeAction(alertId, req, adminPrincipal);

        assertNotNull(response);
        assertEquals("DISMISSED", response.status());
        assertEquals("DISMISS", response.actionTaken());
        assertEquals(0, response.releasedBookingsCount());
        verify(jdbc).update(contains("UPDATE ticketing.scalping_alerts SET status = ?"), eq("DISMISSED"), eq(alertId));
    }

    @Test
    void testTakeAction_NotFound() {
        UUID alertId = UUID.randomUUID();
        when(jdbc.queryForMap(contains("FROM ticketing.scalping_alerts"), eq(alertId)))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        DemoPrincipal adminPrincipal = new DemoPrincipal(UUID.randomUUID(), "admin@eventflow.vn", List.of("ROLE_ADMIN"));
        ScalpingActionRequest req = new ScalpingActionRequest("LOCK_ACCOUNT", "test");

        ApiException ex = assertThrows(ApiException.class, () -> service.takeAction(alertId, req, adminPrincipal));
        assertEquals("SCALPING_ALERT_NOT_FOUND", ex.code);
    }
}
