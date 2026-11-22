package com.haolo.ticketing.checkin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.checkin.CheckInModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.tickets.TicketQrService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfflineCheckInServiceTest {

    private JdbcTemplate jdbc;
    private TicketQrService ticketQrService;
    private StaffCheckInService service;

    private final UUID staffId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final DemoPrincipal staffPrincipal = new DemoPrincipal(staffId, "STAFF");
    private final DemoPrincipal adminPrincipal = new DemoPrincipal(UUID.randomUUID(), "ADMIN");
    private final DemoPrincipal customerPrincipal = new DemoPrincipal(UUID.randomUUID(), "CUSTOMER");

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        ticketQrService = mock(TicketQrService.class);
        service = new StaffCheckInService(jdbc, ticketQrService);
    }

    @Test
    void getOfflineManifest_deniesCustomerAccess() {
        assertThrows(ApiException.class, () -> service.getOfflineManifest(customerPrincipal, eventId));
    }

    @Test
    void getOfflineManifest_returnsTicketsWithQrTokens() {
        // Staff assignment check
        when(jdbc.queryForObject(contains("event_staff_assignments"), eq(Integer.class), eq(staffId), eq(eventId)))
            .thenReturn(1);

        // Event title query
        when(jdbc.queryForList(contains("SELECT title FROM ticketing.events"), eq(eventId)))
            .thenReturn(List.of(Map.of("title", "Concert Offline Test")));

        UUID ticket1 = UUID.randomUUID();
        UUID ticket2 = UUID.randomUUID();

        when(ticketQrService.generateSignedQrToken(eq(ticket1), eq("TCK-001"), eq(eventId), any()))
            .thenReturn("QR-TOKEN-001");
        when(ticketQrService.generateSignedQrToken(eq(ticket2), eq("TCK-002"), eq(eventId), any()))
            .thenReturn("QR-TOKEN-002");

        doAnswer(invocation -> {
            RowMapper<OfflineTicketItem> mapper = invocation.getArgument(1);
            var rs1 = mock(java.sql.ResultSet.class);
            when(rs1.getObject("ticket_id", UUID.class)).thenReturn(ticket1);
            when(rs1.getString("ticket_code")).thenReturn("TCK-001");
            when(rs1.getString("attendee_name")).thenReturn("Khách A");
            when(rs1.getString("ticket_type_name")).thenReturn("VIP");
            when(rs1.getString("seat_label")).thenReturn("A-01");
            when(rs1.getTimestamp("checked_in_at")).thenReturn(null);

            var rs2 = mock(java.sql.ResultSet.class);
            when(rs2.getObject("ticket_id", UUID.class)).thenReturn(ticket2);
            when(rs2.getString("ticket_code")).thenReturn("TCK-002");
            when(rs2.getString("attendee_name")).thenReturn("Khách B");
            when(rs2.getString("ticket_type_name")).thenReturn("GA");
            when(rs2.getString("seat_label")).thenReturn(null);
            when(rs2.getTimestamp("checked_in_at")).thenReturn(Timestamp.from(Instant.now()));

            return List.of(mapper.mapRow(rs1, 0), mapper.mapRow(rs2, 1));
        }).when(jdbc).query(contains("FROM ticketing.tickets t"), any(RowMapper.class), eq(eventId));

        OfflineManifestDto manifest = service.getOfflineManifest(staffPrincipal, eventId);

        assertNotNull(manifest);
        assertEquals(eventId, manifest.eventId());
        assertEquals("Concert Offline Test", manifest.title());
        assertEquals(2, manifest.totalTickets());
        assertEquals(2, manifest.tickets().size());

        OfflineTicketItem t1 = manifest.tickets().get(0);
        assertEquals("TCK-001", t1.ticketCode());
        assertEquals("QR-TOKEN-001", t1.qrToken());
        assertFalse(t1.checkedIn());

        OfflineTicketItem t2 = manifest.tickets().get(1);
        assertEquals("TCK-002", t2.ticketCode());
        assertEquals("QR-TOKEN-002", t2.qrToken());
        assertTrue(t2.checkedIn());
    }

    @Test
    void batchSync_successAndConflictHandling() {
        // Staff assignment check
        when(jdbc.queryForObject(contains("event_staff_assignments"), eq(Integer.class), eq(staffId), eq(eventId)))
            .thenReturn(1);

        UUID ticket1 = UUID.randomUUID();
        UUID ticket2 = UUID.randomUUID();

        // Ticket 1: success insertion
        when(jdbc.queryForObject(contains("INSERT INTO ticketing.ticket_check_ins"), eq(Timestamp.class),
            eq(ticket1), eq(eventId), eq(staffId), anyString(), any(), any()))
            .thenReturn(Timestamp.from(Instant.now()));

        // Ticket 2: duplicate conflict
        when(jdbc.queryForObject(contains("INSERT INTO ticketing.ticket_check_ins"), eq(Timestamp.class),
            eq(ticket2), eq(eventId), eq(staffId), anyString(), any(), any()))
            .thenThrow(new DuplicateKeyException("unique constraint violated"));

        when(jdbc.queryForList(contains("SELECT c.checked_in_at, c.gate_name"), eq(ticket2)))
            .thenReturn(List.of(Map.of(
                "checked_in_at", Timestamp.from(Instant.now().minusSeconds(60)),
                "gate_name", "GATE_A",
                "staff_name", "Staff Leader"
            )));

        OfflineSyncRequest request = new OfflineSyncRequest(
            eventId,
            List.of(
                new OfflineSyncItem("SYNC-01", ticket1, "TCK-001", null, "GATE_OFFLINE", Instant.now().toString(), "Android POS"),
                new OfflineSyncItem("SYNC-02", ticket2, "TCK-002", null, "GATE_OFFLINE", Instant.now().toString(), "Android POS")
            )
        );

        BatchSyncResponse response = service.batchSync(staffPrincipal, request);

        assertNotNull(response);
        assertEquals(eventId, response.eventId());
        assertEquals(2, response.total());
        assertEquals(1, response.syncedCount());
        assertEquals(1, response.conflictCount());

        OfflineSyncResultItem r1 = response.results().get(0);
        assertEquals("SYNC-01", r1.clientSyncId());
        assertEquals("SYNCED", r1.status());

        OfflineSyncResultItem r2 = response.results().get(1);
        assertEquals("SYNC-02", r2.clientSyncId());
        assertEquals("ALREADY_CHECKED_IN", r2.status());
        assertTrue(r2.message().contains("Xung đột"));
    }

    @Test
    void batchSync_resolvesTicketIdFromQrToken() {
        when(jdbc.queryForObject(contains("event_staff_assignments"), eq(Integer.class), eq(staffId), eq(eventId)))
            .thenReturn(1);

        UUID ticketId = UUID.randomUUID();
        String validQrToken = ticketId + ".TCK-QR." + eventId + ".123456.SIG";

        when(ticketQrService.verifyQrToken(validQrToken)).thenReturn(true);
        when(jdbc.queryForObject(contains("INSERT INTO ticketing.ticket_check_ins"), eq(Timestamp.class),
            eq(ticketId), eq(eventId), eq(staffId), anyString(), any(), any()))
            .thenReturn(Timestamp.from(Instant.now()));

        OfflineSyncRequest request = new OfflineSyncRequest(
            eventId,
            List.of(new OfflineSyncItem("SYNC-QR", null, null, validQrToken, "GATE_QR", Instant.now().toString(), "Scanner Device"))
        );

        BatchSyncResponse response = service.batchSync(staffPrincipal, request);
        assertEquals(1, response.syncedCount());
        assertEquals("SYNCED", response.results().get(0).status());
        assertEquals(ticketId, response.results().get(0).ticketId());
    }
}
