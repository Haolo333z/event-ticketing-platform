package com.haolo.ticketing.checkin;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.checkin.CheckInModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
public class StaffCheckInController {

    private final StaffCheckInService service;

    public StaffCheckInController(StaffCheckInService service) {
        this.service = service;
    }

    @GetMapping("/events")
    public ResponseEntity<List<AssignedEventDto>> getAssignedEvents(@AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(service.getAssignedEvents(principal));
    }

    @PostMapping("/events/{eventId}/check-in")
    public ResponseEntity<CheckInResult> checkIn(
            @PathVariable UUID eventId,
            @RequestBody CheckInRequest req,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(service.checkIn(principal, eventId, req));
    }

    @GetMapping("/events/{eventId}/stats")
    public ResponseEntity<CheckInStats> getStats(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(service.getStats(principal, eventId));
    }

    @GetMapping("/events/{eventId}/history")
    public ResponseEntity<List<CheckInHistoryItem>> getHistory(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(service.getRecentHistory(principal, eventId, Math.min(100, Math.max(1, limit))));
    }
}
