package com.haolo.ticketing.transfers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.transfers.TicketTransferModels.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketTransferServiceTest {

    private JdbcTemplate jdbc;
    private TicketTransferService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new TicketTransferService(jdbc);
    }

    @Test
    void testInitiateTransfer_Success() {
        UUID senderId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        DemoPrincipal sender = new DemoPrincipal(senderId, "CUSTOMER");

        TicketTransferService.TicketRow ticketRow = new TicketTransferService.TicketRow(
            ticketId, UUID.randomUUID(), UUID.randomUUID(), 1, "EF-TICKET-01", null, "VALID", senderId
        );

        when(jdbc.query(contains("FROM ticketing.tickets WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(ticketId)))
            .thenReturn(List.of(ticketRow));

        when(jdbc.queryForObject(contains("FROM ticketing.ticket_check_ins"), eq(Integer.class), eq(ticketId)))
            .thenReturn(0);

        when(jdbc.queryForObject(contains("FROM ticketing.ticket_transfers WHERE ticket_id = ?"), eq(Integer.class), eq(ticketId)))
            .thenReturn(0);

        InitiateTransferRequest req = new InitiateTransferRequest(ticketId, "friend@eventflow.vn", "Tặng vé concert");
        TransferItemDto res = service.initiateTransfer(sender, req);

        assertNotNull(res);
        assertEquals("friend@eventflow.vn", res.toEmail());
        assertEquals("PENDING", res.status());
        assertTrue(res.transferToken().startsWith("TRF-"));

        verify(jdbc).update(contains("INSERT INTO ticketing.ticket_transfers"), any(), eq(ticketId), eq(senderId), eq("friend@eventflow.vn"), any(), eq("Tặng vé concert"));
    }

    @Test
    void testInitiateTransfer_AlreadyCheckedIn_ThrowsConflict() {
        UUID senderId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        DemoPrincipal sender = new DemoPrincipal(senderId, "CUSTOMER");

        TicketTransferService.TicketRow ticketRow = new TicketTransferService.TicketRow(
            ticketId, UUID.randomUUID(), UUID.randomUUID(), 1, "EF-TICKET-01", null, "VALID", senderId
        );

        when(jdbc.query(contains("FROM ticketing.tickets WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(ticketId)))
            .thenReturn(List.of(ticketRow));

        when(jdbc.queryForObject(contains("FROM ticketing.ticket_check_ins"), eq(Integer.class), eq(ticketId)))
            .thenReturn(1); // Already checked in!

        InitiateTransferRequest req = new InitiateTransferRequest(ticketId, "friend@eventflow.vn", "Tặng vé concert");
        ApiException ex = assertThrows(ApiException.class, () -> service.initiateTransfer(sender, req));
        assertEquals(409, ex.status);
        assertEquals("TICKET_ALREADY_USED", ex.code);
    }

    @Test
    void testAcceptTransfer_Success_AtomicallyRevokesOldAndIssuesNew() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID oldTicketId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        String token = "TRF-ABCD1234EFGH5678";

        DemoPrincipal recipient = new DemoPrincipal(recipientId, "CUSTOMER");

        TicketTransferService.TransferRecord transferRecord = new TicketTransferService.TransferRecord(
            transferId, oldTicketId, senderId, "recipient@eventflow.vn", "PENDING",
            Timestamp.from(Instant.now().plusSeconds(3600))
        );

        when(jdbc.query(contains("FROM ticketing.ticket_transfers WHERE transfer_token = ? FOR UPDATE"), any(RowMapper.class), eq(token)))
            .thenReturn(List.of(transferRecord));

        TicketTransferService.TicketRow oldTicket = new TicketTransferService.TicketRow(
            oldTicketId, UUID.randomUUID(), UUID.randomUUID(), 1, "EF-OLD-TICKET", null, "VALID", senderId
        );

        when(jdbc.query(contains("FROM ticketing.tickets WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(oldTicketId)))
            .thenReturn(List.of(oldTicket));

        AcceptTransferResponse res = service.acceptTransfer(recipient, token);

        assertNotNull(res);
        assertEquals("ACCEPTED", res.status());
        assertEquals(oldTicketId, res.oldTicketId());
        assertNotNull(res.newTicketId());
        assertTrue(res.newTicketCode().startsWith("EF-TRF-"));

        // Verify old ticket revoked
        verify(jdbc).update(contains("UPDATE ticketing.tickets SET status = 'REVOKED' WHERE id = ?"), eq(oldTicketId));

        // Verify new ticket inserted
        verify(jdbc).update(contains("INSERT INTO ticketing.tickets"), any(UUID.class), any(), any(), eq(1), anyString(), any(), eq(recipientId), eq(oldTicketId));

        // Verify outbox event written
        verify(jdbc).update(contains("INSERT INTO ticketing.outbox_events"), any(), any(), eq(transferId), anyString());
    }

    @Test
    void testAcceptTransfer_SelfTransfer_ThrowsBadRequest() {
        UUID sameUserId = UUID.randomUUID();
        UUID oldTicketId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        String token = "TRF-SELF";

        DemoPrincipal recipient = new DemoPrincipal(sameUserId, "CUSTOMER");

        TicketTransferService.TransferRecord transferRecord = new TicketTransferService.TransferRecord(
            transferId, oldTicketId, sameUserId, "same@eventflow.vn", "PENDING",
            Timestamp.from(Instant.now().plusSeconds(3600))
        );

        when(jdbc.query(contains("FROM ticketing.ticket_transfers WHERE transfer_token = ? FOR UPDATE"), any(RowMapper.class), eq(token)))
            .thenReturn(List.of(transferRecord));

        ApiException ex = assertThrows(ApiException.class, () -> service.acceptTransfer(recipient, token));
        assertEquals(400, ex.status);
        assertEquals("CANNOT_TRANSFER_TO_SELF", ex.code);
    }
}
