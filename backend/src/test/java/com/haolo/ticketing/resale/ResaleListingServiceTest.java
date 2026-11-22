package com.haolo.ticketing.resale;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.resale.ResaleListingModels.*;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResaleListingServiceTest {

    private JdbcTemplate jdbc;
    private ResaleListingService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new ResaleListingService(jdbc);
    }

    @Test
    void testCreateListing_Success() {
        UUID sellerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        DemoPrincipal seller = new DemoPrincipal(sellerId, "CUSTOMER");

        ResaleListingService.TicketDetailRow ticketRow = new ResaleListingService.TicketDetailRow(
            ticketId, UUID.randomUUID(), UUID.randomUUID(), 1, "EF-TICKET-01", null, "VALID",
            sellerId, sellerId, eventId, 500000L, "Concert Vũ 2026", "VIP", "A-12"
        );

        when(jdbc.query(contains("FROM ticketing.tickets t"), any(RowMapper.class), eq(ticketId)))
            .thenReturn(List.of(ticketRow));

        when(jdbc.queryForObject(contains("FROM ticketing.ticket_check_ins"), eq(Integer.class), eq(ticketId)))
            .thenReturn(0);

        when(jdbc.queryForObject(contains("FROM ticketing.ticket_transfers"), eq(Integer.class), eq(ticketId)))
            .thenReturn(0);

        when(jdbc.queryForObject(contains("FROM ticketing.resale_listings"), eq(Integer.class), eq(ticketId)))
            .thenReturn(0);

        CreateResaleListingRequest req = new CreateResaleListingRequest(ticketId, 450000L); // 450k <= 500k
        ResaleListingDto res = service.createListing(seller, req);

        assertNotNull(res);
        assertEquals(ticketId, res.ticketId());
        assertEquals(500000L, res.originalPriceVnd());
        assertEquals(450000L, res.askingPriceVnd());
        assertEquals("LISTED", res.status());

        verify(jdbc).update(contains("INSERT INTO ticketing.resale_listings"), any(), eq(ticketId), eq(sellerId), eq(eventId), eq(500000L), eq(450000L));
    }

    @Test
    void testCreateListing_RejectedWhenPriceExceedsOriginal() {
        UUID sellerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        DemoPrincipal seller = new DemoPrincipal(sellerId, "CUSTOMER");

        ResaleListingService.TicketDetailRow ticketRow = new ResaleListingService.TicketDetailRow(
            ticketId, UUID.randomUUID(), UUID.randomUUID(), 1, "EF-TICKET-01", null, "VALID",
            sellerId, sellerId, UUID.randomUUID(), 500000L, "Concert Vũ 2026", "VIP", "A-12"
        );

        when(jdbc.query(contains("FROM ticketing.tickets t"), any(RowMapper.class), eq(ticketId)))
            .thenReturn(List.of(ticketRow));

        when(jdbc.queryForObject(contains("FROM ticketing.ticket_check_ins"), eq(Integer.class), eq(ticketId)))
            .thenReturn(0);
        when(jdbc.queryForObject(contains("FROM ticketing.ticket_transfers"), eq(Integer.class), eq(ticketId)))
            .thenReturn(0);

        // Asking 600k for a 500k ticket -> Violates anti-scalping rule!
        CreateResaleListingRequest req = new CreateResaleListingRequest(ticketId, 600000L);
        ApiException ex = assertThrows(ApiException.class, () -> service.createListing(seller, req));
        assertEquals(400, ex.status);
        assertEquals("RESALE_PRICE_EXCEEDS_ORIGINAL", ex.code);
    }

    @Test
    void testReserveListing_Success() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        DemoPrincipal buyer = new DemoPrincipal(buyerId, "CUSTOMER");

        ResaleListingService.ListingRow listing = new ResaleListingService.ListingRow(
            listingId, UUID.randomUUID(), sellerId, UUID.randomUUID(), 500000L, 450000L, "LISTED",
            null, null, null, Timestamp.from(Instant.now()), null
        );

        when(jdbc.query(contains("FROM ticketing.resale_listings WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(listingId)))
            .thenReturn(List.of(listing));

        ReserveResaleResponse res = service.reserveListing(buyer, listingId);
        assertNotNull(res);
        assertEquals("RESERVED", res.status());
        assertNotNull(res.lockExpiresAt());

        verify(jdbc).update(contains("UPDATE ticketing.resale_listings"), eq(buyerId), any(Timestamp.class), eq(listingId));
    }

    @Test
    void testReserveListing_CannotBuyOwnTicket() {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        DemoPrincipal buyer = new DemoPrincipal(sellerId, "CUSTOMER"); // Same ID as seller

        ResaleListingService.ListingRow listing = new ResaleListingService.ListingRow(
            listingId, UUID.randomUUID(), sellerId, UUID.randomUUID(), 500000L, 450000L, "LISTED",
            null, null, null, Timestamp.from(Instant.now()), null
        );

        when(jdbc.query(contains("FROM ticketing.resale_listings WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(listingId)))
            .thenReturn(List.of(listing));

        ApiException ex = assertThrows(ApiException.class, () -> service.reserveListing(buyer, listingId));
        assertEquals(400, ex.status);
        assertEquals("CANNOT_BUY_OWN_TICKET", ex.code);
    }

    @Test
    void testSettlePurchase_Success_AtomicallyRevokesOldAndIssuesNew() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID oldTicketId = UUID.randomUUID();
        DemoPrincipal buyer = new DemoPrincipal(buyerId, "CUSTOMER");

        ResaleListingService.ListingRow listing = new ResaleListingService.ListingRow(
            listingId, oldTicketId, sellerId, UUID.randomUUID(), 500000L, 450000L, "RESERVED",
            buyerId, Timestamp.from(Instant.now().plusSeconds(300)), null, Timestamp.from(Instant.now()), null
        );

        when(jdbc.query(contains("FROM ticketing.resale_listings WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(listingId)))
            .thenReturn(List.of(listing));

        ResaleListingService.TicketDetailRow oldTicket = new ResaleListingService.TicketDetailRow(
            oldTicketId, UUID.randomUUID(), UUID.randomUUID(), 1, "EF-OLD-01", null, "VALID",
            sellerId, sellerId, UUID.randomUUID(), 500000L, "Event", "GA", null
        );

        when(jdbc.query(contains("FROM ticketing.tickets"), any(RowMapper.class), eq(oldTicketId)))
            .thenReturn(List.of(oldTicket));

        SettleResaleResponse res = service.settlePurchase(buyer, listingId);
        assertNotNull(res);
        assertEquals("SOLD", res.status());
        assertTrue(res.newTicketCode().startsWith("EF-RSL-"));

        // Verify old ticket revoked
        verify(jdbc).update(contains("UPDATE ticketing.tickets SET status = 'REVOKED'"), eq(oldTicketId));

        // Verify new ticket issued to buyer
        verify(jdbc).update(contains("INSERT INTO ticketing.tickets"), any(UUID.class), any(), any(), eq(1), startsWith("EF-RSL-"), eq(buyerId), eq(oldTicketId));

        // Verify listing marked SOLD
        verify(jdbc).update(contains("UPDATE ticketing.resale_listings"), eq(buyerId), eq(listingId));

        // Verify Kafka Outbox event written
        verify(jdbc).update(contains("INSERT INTO ticketing.outbox_events"), any(UUID.class), any(UUID.class), eq(listingId), contains("listingId"));
    }

    @Test
    void testSettlePurchase_ExpiredReservation_ThrowsBadRequest() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        DemoPrincipal buyer = new DemoPrincipal(buyerId, "CUSTOMER");

        // Expired 10 seconds ago
        ResaleListingService.ListingRow listing = new ResaleListingService.ListingRow(
            listingId, UUID.randomUUID(), sellerId, UUID.randomUUID(), 500000L, 450000L, "RESERVED",
            buyerId, Timestamp.from(Instant.now().minusSeconds(10)), null, Timestamp.from(Instant.now()), null
        );

        when(jdbc.query(contains("FROM ticketing.resale_listings WHERE id = ? FOR UPDATE"), any(RowMapper.class), eq(listingId)))
            .thenReturn(List.of(listing));

        ApiException ex = assertThrows(ApiException.class, () -> service.settlePurchase(buyer, listingId));
        assertEquals(400, ex.status);
        assertEquals("RESERVATION_EXPIRED", ex.code);
    }
}
