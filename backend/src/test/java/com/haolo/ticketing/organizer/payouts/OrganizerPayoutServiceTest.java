package com.haolo.ticketing.organizer.payouts;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.EventSecurityService;
import com.haolo.ticketing.organizer.payouts.PayoutModels.*;
import com.haolo.ticketing.shared.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrganizerPayoutServiceTest {

    private JdbcTemplate jdbc;
    private EventSecurityService securityService;
    private OrganizerPayoutService payoutService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        securityService = mock(EventSecurityService.class);
        payoutService = new OrganizerPayoutService(jdbc, securityService);
    }

    @Test
    void getPayoutSummaryCalculatesProfessionalTier5PercentFee() {
        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        DemoPrincipal principal = new DemoPrincipal(accountId, "ORGANIZER", List.of("ORGANIZER"));

        Map<String, Object> eventRow = Map.of(
            "id", eventId,
            "title", "Đại nhạc hội Sao Kim 2026",
            "organizer_id", UUID.randomUUID(),
            "saas_plan", "PROFESSIONAL"
        );
        when(jdbc.queryForList(contains("FROM ticketing.events e"), eq(eventId)))
            .thenReturn(List.of(eventRow));

        // 100M VND gross revenue
        when(jdbc.queryForObject(contains("SUM(total_amount_vnd)"), eq(Long.class), eq(eventId)))
            .thenReturn(100_000_000L);
        when(jdbc.queryForObject(contains("SELECT count(*) FROM ticketing.bookings"), eq(Long.class), eq(eventId)))
            .thenReturn(200L);
        // 0 refund
        when(jdbc.queryForObject(contains("refund_attempts"), eq(Long.class), eq(eventId)))
            .thenReturn(0L);
        // 20M already paid
        when(jdbc.queryForObject(contains("organizer_payouts"), eq(Long.class), eq(eventId)))
            .thenReturn(20_000_000L);

        PayoutSummaryDto summary = payoutService.getPayoutSummary(principal, eventId);
        assertNotNull(summary);
        assertEquals("PROFESSIONAL", summary.saasPlan());
        assertEquals(5.00, summary.platformFeePercent());
        assertEquals(100_000_000L, summary.grossRevenueVnd());
        assertEquals(5_000_000L, summary.platformFeeVnd()); // 5% of 100M
        assertEquals(95_000_000L, summary.netPayoutTotalVnd()); // 95M
        assertEquals(20_000_000L, summary.totalPayoutsRequestedVnd());
        assertEquals(75_000_000L, summary.availableBalanceVnd()); // 95M - 20M = 75M
    }

    @Test
    void requestPayoutFailsWhenExceedingAvailableBalance() {
        UUID accountId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        DemoPrincipal principal = new DemoPrincipal(accountId, "ORGANIZER", List.of("ORGANIZER"));

        when(securityService.getOrganizerProfileId(accountId)).thenReturn(profileId);

        Map<String, Object> eventRow = Map.of(
            "id", eventId,
            "title", "Concert",
            "organizer_id", profileId,
            "saas_plan", "PROFESSIONAL"
        );
        when(jdbc.queryForList(contains("FROM ticketing.events e"), eq(eventId)))
            .thenReturn(List.of(eventRow));
        when(jdbc.queryForObject(contains("SUM(total_amount_vnd)"), eq(Long.class), eq(eventId)))
            .thenReturn(10_000_000L);
        when(jdbc.queryForObject(contains("SELECT count(*) FROM ticketing.bookings"), eq(Long.class), eq(eventId)))
            .thenReturn(10L);
        when(jdbc.queryForObject(contains("refund_attempts"), eq(Long.class), eq(eventId)))
            .thenReturn(0L);
        when(jdbc.queryForObject(contains("organizer_payouts"), eq(Long.class), eq(eventId)))
            .thenReturn(9_000_000L); // 9.5M net total - 9M requested = 500k available

        RequestPayoutRequest req = new RequestPayoutRequest(
            eventId, 1_000_000L, "Vietcombank", "001100223344", "LE HUU HAO"
        );

        ApiException ex = assertThrows(ApiException.class, () -> payoutService.requestPayout(principal, req));
        assertEquals(400, ex.status);
        assertEquals("INSUFFICIENT_PAYOUT_BALANCE", ex.code);
    }

    @Test
    void requestPayoutSuccessful() {
        UUID accountId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        DemoPrincipal principal = new DemoPrincipal(accountId, "ORGANIZER", List.of("ORGANIZER"));

        when(securityService.getOrganizerProfileId(accountId)).thenReturn(profileId);

        Map<String, Object> eventRow = Map.of(
            "id", eventId,
            "title", "Tech Conference 2026",
            "organizer_id", profileId,
            "saas_plan", "STARTER" // 8% fee
        );
        when(jdbc.queryForList(contains("FROM ticketing.events e"), eq(eventId)))
            .thenReturn(List.of(eventRow));
        when(jdbc.queryForObject(contains("SUM(total_amount_vnd)"), eq(Long.class), eq(eventId)))
            .thenReturn(50_000_000L);
        when(jdbc.queryForObject(contains("SELECT count(*) FROM ticketing.bookings"), eq(Long.class), eq(eventId)))
            .thenReturn(50L);
        when(jdbc.queryForObject(contains("refund_attempts"), eq(Long.class), eq(eventId)))
            .thenReturn(0L);
        when(jdbc.queryForObject(contains("organizer_payouts"), eq(Long.class), eq(eventId)))
            .thenReturn(0L);

        RequestPayoutRequest req = new RequestPayoutRequest(
            eventId, 10_000_000L, "Techcombank", "19033445566", "LE HUU HAO"
        );

        OrganizerPayoutDto mockCreated = new OrganizerPayoutDto(
            UUID.randomUUID(), profileId, "EventFlow Org", eventId, "Tech Conference 2026",
            10_869_565L, 8.00, 869_565L, 10_000_000L, "Techcombank", "19033445566",
            "LE HUU HAO", "REQUESTED", Instant.now(), null, null
        );
        when(jdbc.query(contains("FROM ticketing.organizer_payouts p"), any(RowMapper.class), any(UUID.class)))
            .thenReturn(List.of(mockCreated));

        OrganizerPayoutDto created = payoutService.requestPayout(principal, req);
        assertNotNull(created);
        assertEquals(10_000_000L, created.netPayoutVnd());
        assertEquals("REQUESTED", created.status());

        verify(jdbc).update(contains("INSERT INTO ticketing.organizer_payouts"), any(UUID.class), eq(profileId),
            eq(eventId), eq(10869565L), eq(8.0d), eq(869565L), eq(10_000_000L), eq("Techcombank"),
            eq("19033445566"), eq("LE HUU HAO"));

        verify(jdbc).update(contains("INSERT INTO ticketing.audit_logs"), eq(accountId),
            anyString(), anyString());
    }

    @Test
    void processPayoutAdminApprovesAndMarksPaid() {
        UUID adminId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        DemoPrincipal admin = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));

        when(jdbc.queryForList(contains("FROM ticketing.organizer_payouts WHERE id = ? FOR UPDATE"), eq(payoutId)))
            .thenReturn(List.of(Map.of("id", payoutId, "status", "REQUESTED", "net_payout_vnd", 5_000_000L)));

        ProcessPayoutRequest req = new ProcessPayoutRequest("PAID", "Đã chuyển khoản thành công qua Vietcombank");
        ProcessPayoutResponse resp = payoutService.processPayout(admin, payoutId, req);

        assertNotNull(resp);
        assertEquals("PAID", resp.status());
        verify(jdbc).update(contains("UPDATE ticketing.organizer_payouts SET status = ?"), eq("PAID"), any(), eq("Đã chuyển khoản thành công qua Vietcombank"), eq(payoutId));
    }
}
