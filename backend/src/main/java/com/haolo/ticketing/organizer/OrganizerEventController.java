package com.haolo.ticketing.organizer;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.OrganizerModels.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/organizer")
public class OrganizerEventController {

    private final OrganizerService organizerService;

    public OrganizerEventController(OrganizerService organizerService) {
        this.organizerService = organizerService;
    }

    @GetMapping("/profile")
    public ResponseEntity<OrganizerProfile> getProfile(@AuthenticationPrincipal DemoPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        }
        OrganizerProfile profile = organizerService.getProfile(principal.accountId());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/profile")
    public ResponseEntity<OrganizerProfile> saveProfile(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody OrganizerProfileRequest req
    ) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        }
        OrganizerProfile profile = organizerService.saveProfile(principal.accountId(), req);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/venues")
    public ResponseEntity<List<VenueDto>> listVenues() {
        return ResponseEntity.ok(organizerService.listVenues());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<EventCategoryDto>> listCategories() {
        return ResponseEntity.ok(organizerService.listCategories());
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<List<OrganizerEventDto>> listMyEvents(@AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(organizerService.listMyEvents(principal));
    }

    @PostMapping("/events")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<OrganizerEventDto> createEvent(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody CreateOrganizerEventRequest req
    ) {
        OrganizerEventDto event = organizerService.createDraftEvent(principal, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<OrganizerEventDto> getEventDetail(
        @PathVariable UUID id,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        return ResponseEntity.ok(organizerService.getEventDetail(id, principal));
    }

    @PutMapping("/events/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<OrganizerEventDto> updateEvent(
        @PathVariable UUID id,
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody UpdateOrganizerEventRequest req
    ) {
        return ResponseEntity.ok(organizerService.updateEvent(id, principal, req));
    }

    @PostMapping("/events/{id}/publish")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<OrganizerEventDto> publishEvent(
        @PathVariable UUID id,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        return ResponseEntity.ok(organizerService.publishEvent(id, principal));
    }

    @PostMapping("/events/{id}/ticket-types")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<OrganizerEventDto> addTicketType(
        @PathVariable UUID id,
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody AddTicketTypeRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizerService.addTicketType(id, principal, req));
    }

    @GetMapping("/events/{id}/staff")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<List<StaffAssignmentDto>> listStaffAssignments(
        @PathVariable UUID id,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        return ResponseEntity.ok(organizerService.listStaffAssignments(id, principal));
    }

    @PostMapping("/events/{id}/staff")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<StaffAssignmentDto> assignStaff(
        @PathVariable UUID id,
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody AssignStaffRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizerService.assignStaff(id, principal, req));
    }

    @DeleteMapping("/events/{id}/staff/{staffUserId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<Void> revokeStaff(
        @PathVariable UUID id,
        @PathVariable UUID staffUserId,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        organizerService.revokeStaff(id, staffUserId, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staff-candidates")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<List<StaffCandidateDto>> searchStaffCandidates(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(organizerService.searchStaffCandidates(q));
    }
}
