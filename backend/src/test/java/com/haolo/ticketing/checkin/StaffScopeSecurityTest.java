package com.haolo.ticketing.checkin;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.checkin.CheckInModels.CheckInRequest;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.tickets.TicketQrService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffScopeSecurityTest {

    private JdbcTemplate jdbc;
    private TicketQrService qrService;
    private StaffCheckInService service;

    private final UUID staffAId = UUID.randomUUID();
    private final UUID staffBId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();

    private final UUID event1Id = UUID.randomUUID();
    private final UUID event2Id = UUID.randomUUID();

    private final DemoPrincipal staffAPrincipal = new DemoPrincipal(staffAId, "STAFF", List.of("STAFF"));
    private final DemoPrincipal staffBPrincipal = new DemoPrincipal(staffBId, "STAFF", List.of("STAFF"));
    private final DemoPrincipal adminPrincipal = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));
    private final DemoPrincipal customerPrincipal = new DemoPrincipal(customerId, "CUSTOMER", List.of("CUSTOMER"));
    private final DemoPrincipal organizerPrincipal = new DemoPrincipal(organizerId, "ORGANIZER", List.of("ORGANIZER"));

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        qrService = Mockito.mock(TicketQrService.class);
        service = new StaffCheckInService(jdbc, qrService);
    }

    @Test
    void staffAAllowedForEvent1WhenAssigned() {
        when(jdbc.queryForObject(contains("SELECT count(*) FROM ticketing.event_staff_assignments"), eq(Integer.class), eq(staffAId), eq(event1Id)))
            .thenReturn(1);

        assertDoesNotThrow(() -> service.assertStaffAccess(staffAPrincipal, event1Id));
    }

    @Test
    void staffADeniedForEvent2WhenNotAssigned() {
        when(jdbc.queryForObject(contains("SELECT count(*) FROM ticketing.event_staff_assignments"), eq(Integer.class), eq(staffAId), eq(event2Id)))
            .thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> service.assertStaffAccess(staffAPrincipal, event2Id));
        assertEquals(403, ex.status);
        assertEquals("EVENT_SCOPE_DENIED", ex.code);
        assertTrue(ex.getMessage().contains("không được phân công"));
    }

    @Test
    void staffBDeniedForEvent1WhenNotAssigned() {
        when(jdbc.queryForObject(contains("SELECT count(*) FROM ticketing.event_staff_assignments"), eq(Integer.class), eq(staffBId), eq(event1Id)))
            .thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> service.assertStaffAccess(staffBPrincipal, event1Id));
        assertEquals(403, ex.status);
        assertEquals("EVENT_SCOPE_DENIED", ex.code);
    }

    @Test
    void adminBypassesStaffAssignmentCheckForAllEvents() {
        assertDoesNotThrow(() -> service.assertStaffAccess(adminPrincipal, event1Id));
        assertDoesNotThrow(() -> service.assertStaffAccess(adminPrincipal, event2Id));
        verify(jdbc, never()).queryForObject(contains("SELECT count(*) FROM ticketing.event_staff_assignments"), eq(Integer.class), any(), any());
    }

    @Test
    void customerDeniedWithForbidden() {
        ApiException ex = assertThrows(ApiException.class, () -> service.assertStaffAccess(customerPrincipal, event1Id));
        assertEquals(403, ex.status);
        assertEquals("FORBIDDEN", ex.code);
    }

    @Test
    void organizerDeniedWithForbidden() {
        ApiException ex = assertThrows(ApiException.class, () -> service.assertStaffAccess(organizerPrincipal, event1Id));
        assertEquals(403, ex.status);
        assertEquals("FORBIDDEN", ex.code);
    }
}
