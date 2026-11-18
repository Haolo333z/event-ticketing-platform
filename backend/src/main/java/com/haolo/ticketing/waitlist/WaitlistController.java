package com.haolo.ticketing.waitlist;

import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.waitlist.WaitingRoomService.WaitingRoomStatus;
import com.haolo.ticketing.waitlist.WaitlistService.JoinWaitlistRequest;
import com.haolo.ticketing.waitlist.WaitlistService.WaitlistEntry;

@RestController
public class WaitlistController {

    public record EnterWaitingRoomRequest(String sessionId) {}
    public record SimulateSurgeRequest(Integer maxActiveUsers, Integer simulatedQueuedUsers) {}

    private final WaitlistService waitlistService;
    private final WaitingRoomService waitingRoomService;

    public WaitlistController(WaitlistService waitlistService, WaitingRoomService waitingRoomService) {
        this.waitlistService = waitlistService;
        this.waitingRoomService = waitingRoomService;
    }

    @PostMapping("/api/events/{eventId}/waitlist")
    @PreAuthorize("hasRole('CUSTOMER')")
    public WaitlistEntry joinWaitlist(
        @PathVariable UUID eventId,
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody(required = false) JoinWaitlistRequest req
    ) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        return waitlistService.join(eventId, principal.accountId(), req);
    }

    @PostMapping("/api/events/{eventId}/waitlist/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Object> cancelWaitlist(
        @PathVariable UUID eventId,
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        return waitlistService.cancel(eventId, principal.accountId());
    }

    @GetMapping("/api/me/waitlists")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Object> myWaitlists(@AuthenticationPrincipal DemoPrincipal principal) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        return Map.of("items", waitlistService.listMine(principal.accountId()));
    }

    @PostMapping("/api/events/{eventId}/waiting-room/enter")
    public WaitingRoomStatus enterWaitingRoom(
        @PathVariable UUID eventId,
        @RequestBody(required = false) EnterWaitingRoomRequest req
    ) {
        return waitingRoomService.enter(eventId, req != null ? req.sessionId() : null);
    }

    @GetMapping("/api/events/{eventId}/waiting-room/status")
    public WaitingRoomStatus waitingRoomStatus(
        @PathVariable UUID eventId,
        @RequestParam String sessionId
    ) {
        return waitingRoomService.status(eventId, sessionId);
    }

    @PostMapping("/api/events/{eventId}/waiting-room/simulate-surge")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Map<String, Object> simulateSurge(
        @PathVariable UUID eventId,
        @RequestBody(required = false) SimulateSurgeRequest req
    ) {
        int maxActive = req != null && req.maxActiveUsers() != null ? req.maxActiveUsers() : 2;
        int simQueued = req != null && req.simulatedQueuedUsers() != null ? req.simulatedQueuedUsers() : 5;
        return waitingRoomService.configureSurge(eventId, maxActive, simQueued);
    }
}
