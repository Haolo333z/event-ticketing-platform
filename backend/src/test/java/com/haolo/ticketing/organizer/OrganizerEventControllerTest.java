package com.haolo.ticketing.organizer;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.OrganizerModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrganizerEventControllerTest {

    private OrganizerService service;
    private OrganizerEventController controller;

    private final UUID userId = UUID.randomUUID();
    private final DemoPrincipal principal = new DemoPrincipal(userId, "ORGANIZER", List.of("ORGANIZER"));

    @BeforeEach
    void setUp() {
        service = Mockito.mock(OrganizerService.class);
        controller = new OrganizerEventController(service);
    }

    @Test
    void testGetAndSaveProfile() {
        OrganizerProfile profile = new OrganizerProfile(
            UUID.randomUUID(), userId, "Công ty ABC", "abc@test.com", "0901234567", null, "ACTIVE", Instant.now(), Instant.now()
        );
        when(service.getProfile(userId)).thenReturn(profile);
        when(service.saveProfile(eq(userId), any())).thenReturn(profile);

        ResponseEntity<OrganizerProfile> resGet = controller.getProfile(principal);
        assertEquals(HttpStatus.OK, resGet.getStatusCode());
        assertEquals("Công ty ABC", resGet.getBody().organizationName());

        OrganizerProfileRequest req = new OrganizerProfileRequest("Công ty ABC", "abc@test.com", "0901234567", null);
        ResponseEntity<OrganizerProfile> resSave = controller.saveProfile(principal, req);
        assertEquals(HttpStatus.OK, resSave.getStatusCode());
        assertEquals("Công ty ABC", resSave.getBody().organizationName());
    }

    @Test
    void testVenuesAndCategories() {
        when(service.listVenues()).thenReturn(List.of(
            new VenueDto(UUID.randomUUID(), "Nhà hát Lớn", "Hà Nội", "Hà Nội", "Vietnam", 600, null)
        ));
        when(service.listCategories()).thenReturn(List.of(
            new EventCategoryDto("CONCERT", "Concert", "Ca nhạc", "music")
        ));

        assertEquals(1, controller.listVenues().getBody().size());
        assertEquals(1, controller.listCategories().getBody().size());
    }

    @Test
    void testEventCrudAndPublish() {
        UUID eventId = UUID.randomUUID();
        OrganizerEventDto event = new OrganizerEventDto(
            eventId, "Concert 2026", "Desc", "Hà Nội", null, "CONCERT", UUID.randomUUID(),
            Instant.now(), Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
            "DRAFT", Collections.emptyList(), 0, 0, Instant.now()
        );

        when(service.listMyEvents(principal)).thenReturn(List.of(event));
        when(service.createDraftEvent(eq(principal), any())).thenReturn(event);
        when(service.getEventDetail(eventId, principal)).thenReturn(event);
        when(service.updateEvent(eq(eventId), eq(principal), any())).thenReturn(event);

        OrganizerEventDto published = new OrganizerEventDto(
            eventId, "Concert 2026", "Desc", "Hà Nội", null, "CONCERT", UUID.randomUUID(),
            Instant.now(), Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
            "PUBLISHED", Collections.emptyList(), 1000, 0, Instant.now()
        );
        when(service.publishEvent(eventId, principal)).thenReturn(published);

        // List
        assertEquals(1, controller.listMyEvents(principal).getBody().size());

        // Create
        CreateOrganizerEventRequest createReq = new CreateOrganizerEventRequest(
            "Concert 2026", "Desc", "Hà Nội", null, "CONCERT", Instant.now(), Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)
        );
        var createdRes = controller.createEvent(principal, createReq);
        assertEquals(HttpStatus.CREATED, createdRes.getStatusCode());

        // Detail
        assertEquals("Concert 2026", controller.getEventDetail(eventId, principal).getBody().title());

        // Publish
        var pubRes = controller.publishEvent(eventId, principal);
        assertEquals("PUBLISHED", pubRes.getBody().status());
    }

    @Test
    void testStaffAssignmentAndRevoke() {
        UUID eventId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        StaffAssignmentDto assignment = new StaffAssignmentDto(
            UUID.randomUUID(), eventId, staffId, "Staff Nguyen", "staff@test.com", userId, Instant.now(), null
        );

        when(service.listStaffAssignments(eventId, principal)).thenReturn(List.of(assignment));
        when(service.assignStaff(eq(eventId), eq(principal), any())).thenReturn(assignment);

        assertEquals(1, controller.listStaffAssignments(eventId, principal).getBody().size());

        AssignStaffRequest assignReq = new AssignStaffRequest(staffId, null);
        var assignedRes = controller.assignStaff(eventId, principal, assignReq);
        assertEquals(HttpStatus.CREATED, assignedRes.getStatusCode());

        var revokeRes = controller.revokeStaff(eventId, staffId, principal);
        assertEquals(HttpStatus.NO_CONTENT, revokeRes.getStatusCode());
        verify(service, times(1)).revokeStaff(eventId, staffId, principal);
    }
}
