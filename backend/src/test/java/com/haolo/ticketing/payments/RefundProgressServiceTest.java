package com.haolo.ticketing.payments;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.payments.RefundProgressModels.RefundProgressResponse;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefundProgressServiceTest {

    private JdbcTemplate jdbc;
    private RefundProgressService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new RefundProgressService(jdbc);
    }

    @Test
    void testGetRefundProgress_Settled_Success() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        DemoPrincipal principal = new DemoPrincipal(userId, "CUSTOMER", List.of("CUSTOMER"));

        when(jdbc.query(contains("FROM ticketing.bookings"), any(RowMapper.class), eq(bookingId)))
            .thenReturn(List.of(new RefundProgressService.BookingInfo(userId, "REFUND_REQUIRED", 300000L)));

        when(jdbc.query(contains("FROM ticketing.refund_attempts"), any(RowMapper.class), eq(bookingId)))
            .thenReturn(List.of(new RefundProgressService.RefundAttemptInfo(
                UUID.randomUUID(), 300000L, "Late payment after expired", "SETTLED", null, null
            )));

        RefundProgressResponse res = service.getRefundProgress(principal, bookingId);
        assertNotNull(res);
        assertEquals(bookingId, res.bookingId());
        assertEquals("REFUND_REQUIRED", res.bookingStatus());
        assertEquals(300000L, res.amountVnd());
        assertEquals(3, res.steps().size());
        assertEquals("COMPLETED", res.steps().get(0).status());
        assertEquals("COMPLETED", res.steps().get(1).status());
        assertEquals("COMPLETED", res.steps().get(2).status());
    }

    @Test
    void testGetRefundProgress_ForbiddenForOtherUser() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        DemoPrincipal otherPrincipal = new DemoPrincipal(otherUserId, "CUSTOMER", List.of("CUSTOMER"));

        when(jdbc.query(contains("FROM ticketing.bookings"), any(RowMapper.class), eq(bookingId)))
            .thenReturn(List.of(new RefundProgressService.BookingInfo(ownerId, "REFUND_REQUIRED", 300000L)));

        ApiException ex = assertThrows(ApiException.class, () -> service.getRefundProgress(otherPrincipal, bookingId));
        assertEquals(403, ex.status);
        assertEquals("FORBIDDEN", ex.code);
    }
}
