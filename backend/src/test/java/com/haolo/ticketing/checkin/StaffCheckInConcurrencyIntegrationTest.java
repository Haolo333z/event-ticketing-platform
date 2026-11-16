package com.haolo.ticketing.checkin;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.haolo.ticketing.checkin.CheckInModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.tickets.TicketQrService;

import static org.junit.jupiter.api.Assertions.*;

class StaffCheckInConcurrencyIntegrationTest {

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

    private static final String URL = "jdbc:postgresql://localhost:5432/ticketing_demo";
    private static final String USER = "ticketing";
    private static final String PASS = "1454158d66ab469493b8bbca1669b0681465533ede684524a1919126f88b3b0b";

    private JdbcTemplate jdbc;
    private TicketQrService qrService;
    private StaffCheckInService service;

    private UUID staffUserId;
    private UUID customerUserId;
    private UUID eventId;
    private UUID ticketTypeId;
    private UUID bookingId;
    private UUID bookingItemId;
    private UUID ticketId;
    private String ticketCode;

    private DemoPrincipal staffPrincipal;
    private DemoPrincipal customerPrincipal;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource(URL, USER, PASS);
        jdbc = new JdbcTemplate(ds);
        qrService = new TicketQrService(jdbc, "test-concurrency-qr-signing-key-secret-2026!!");
        service = new StaffCheckInService(jdbc, qrService);

        staffUserId = UUID.randomUUID();
        customerUserId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        bookingItemId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        ticketCode = "TKT-CONC-" + ticketId.toString().substring(0, 8).toUpperCase();

        // 1. Seed users
        jdbc.update("INSERT INTO ticketing.users (id, display_name, email, demo_role) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
            staffUserId, "Staff Concurrency", "staff-" + staffUserId + "@test.com", "STAFF");
        jdbc.update("INSERT INTO ticketing.user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            staffUserId, "STAFF");

        jdbc.update("INSERT INTO ticketing.users (id, display_name, email, demo_role) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
            customerUserId, "Customer Concurrency", "cust-" + customerUserId + "@test.com", "CUSTOMER");
        jdbc.update("INSERT INTO ticketing.user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            customerUserId, "CUSTOMER");

        staffPrincipal = new DemoPrincipal(staffUserId, "STAFF", List.of("STAFF"));
        customerPrincipal = new DemoPrincipal(customerUserId, "CUSTOMER", List.of("CUSTOMER"));

        // 2. Seed event
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update(
            "INSERT INTO ticketing.events (id, title, description, venue, sale_start_at, sale_end_at, starts_at, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            eventId, "Concert Concurrency Check-in", "Description", "Sân Mỹ Đình",
            Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
            Timestamp.from(now.plus(1, ChronoUnit.DAYS)),
            Timestamp.from(now.plus(2, ChronoUnit.DAYS)),
            "PUBLISHED"
        );

        // 3. Assign staff to event
        jdbc.update(
            "INSERT INTO ticketing.event_staff_assignments (id, event_id, staff_user_id, assigned_by) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), eventId, staffUserId, staffUserId
        );

        // 4. Seed ticket type & inventory
        jdbc.update(
            "INSERT INTO ticketing.ticket_types (id, event_id, name, mode, price_vnd, active) VALUES (?, ?, ?, ?, ?, ?)",
            ticketTypeId, eventId, "Vé GA Test", "GA", 250000L, true
        );
        jdbc.update(
            "INSERT INTO ticketing.inventory_ga (ticket_type_id, capacity, held_count, sold_count) VALUES (?, ?, ?, ?)",
            ticketTypeId, 100, 0, 1
        );

        // 5. Seed booking (CONFIRMED) & ticket
        jdbc.update(
            "INSERT INTO ticketing.bookings (id, account_id, event_id, ticket_type_id, payment_intent_id, status, created_at, expires_at, total_amount_vnd, aggregate_version) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            bookingId, customerUserId, eventId, ticketTypeId, UUID.randomUUID(), "CONFIRMED",
            Timestamp.from(now.minus(10, ChronoUnit.MINUTES)),
            Timestamp.from(now.plus(1, ChronoUnit.HOURS)),
            250000L, 1L
        );

        jdbc.update(
            "INSERT INTO ticketing.booking_items (id, booking_id, event_id, ticket_type_id, quantity, unit_price_vnd, total_price_vnd) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            bookingItemId, bookingId, eventId, ticketTypeId, 1, 250000L, 250000L
        );

        jdbc.update(
            "INSERT INTO ticketing.tickets (id, booking_id, booking_item_id, ticket_ordinal, code) VALUES (?, ?, ?, ?, ?)",
            ticketId, bookingId, bookingItemId, 1, ticketCode
        );
    }

    @Test
    void testAtomicAntiDuplicateCheckInUnderHighConcurrency() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        List<CheckInResult> successfulResults = Collections.synchronizedList(new ArrayList<>());
        List<String> conflictMessages = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int gateIndex = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Wait for all threads to align
                    CheckInRequest req = new CheckInRequest(null, ticketCode, "GATE_" + (char) ('A' + gateIndex), "Device-" + gateIndex);
                    CheckInResult res = service.checkIn(staffPrincipal, eventId, req);
                    successCount.incrementAndGet();
                    successfulResults.add(res);
                } catch (ApiException ex) {
                    if (ex.status == 409 && "TICKET_ALREADY_USED".equals(ex.code)) {
                        conflictCount.incrementAndGet();
                        conflictMessages.add(ex.getMessage());
                    } else {
                        otherErrors.incrementAndGet();
                        ex.printStackTrace();
                    }
                } catch (Exception ex) {
                    otherErrors.incrementAndGet();
                    ex.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "All threads should be ready");
        startLatch.countDown(); // FIRE!
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        // Invariant 1: Exactly 1 thread succeeds
        assertEquals(1, successCount.get(), "EXACTLY 1 check-in request must succeed");

        // Invariant 2: Exactly 9 threads fail with 409 Conflict
        assertEquals(threadCount - 1, conflictCount.get(), "All other requests must receive 409 Conflict");
        assertEquals(0, otherErrors.get(), "There must be zero unexpected errors");

        // Invariant 3: The database table ticketing.ticket_check_ins contains EXACTLY 1 row for that ticket
        Integer checkInRowCount = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.ticket_check_ins WHERE ticket_id = ?",
            Integer.class,
            ticketId
        );
        assertEquals(1, checkInRowCount, "Database must contain exactly 1 check-in record for the ticket");

        // Invariant 4: Conflict messages contain the warning and gate info
        assertFalse(conflictMessages.isEmpty());
        for (String msg : conflictMessages) {
            assertTrue(msg.contains("CẢNH BÁO: Vé này đã được quét"), "Error message must warn about previous check-in: " + msg);
        }
    }

    @Test
    void testStaffEventScopeIsolation() {
        UUID unassignedEventId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update(
            "INSERT INTO ticketing.events (id, title, description, venue, sale_start_at, sale_end_at, starts_at, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            unassignedEventId, "Other Event", "Desc", "Hanoi",
            Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
            Timestamp.from(now.plus(1, ChronoUnit.DAYS)),
            Timestamp.from(now.plus(2, ChronoUnit.DAYS)),
            "PUBLISHED"
        );

        // Staff tries to check in for unassigned event -> 403 EVENT_SCOPE_DENIED
        ApiException ex = assertThrows(ApiException.class, () ->
            service.checkIn(staffPrincipal, unassignedEventId, new CheckInRequest(null, ticketCode, "GATE_A", null))
        );
        assertEquals(403, ex.status);
        assertEquals("EVENT_SCOPE_DENIED", ex.code);

        // Customer tries to check in -> 403 FORBIDDEN
        ex = assertThrows(ApiException.class, () ->
            service.checkIn(customerPrincipal, eventId, new CheckInRequest(null, ticketCode, "GATE_A", null))
        );
        assertEquals(403, ex.status);
        assertEquals("FORBIDDEN", ex.code);
    }
}
