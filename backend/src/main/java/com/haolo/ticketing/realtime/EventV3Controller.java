package com.haolo.ticketing.realtime;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.realtime.BestSeatsService.BestSeatsResult;
import com.haolo.ticketing.realtime.EventV3Service.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
public class EventV3Controller {

    private final EventV3Service eventV3Service;
    private final BestSeatsService bestSeatsService;
    private final EventStreamService streamService;
    private final CalendarIcsService calendarIcsService;

    public EventV3Controller(
        EventV3Service eventV3Service,
        BestSeatsService bestSeatsService,
        EventStreamService streamService,
        CalendarIcsService calendarIcsService
    ) {
        this.eventV3Service = eventV3Service;
        this.bestSeatsService = bestSeatsService;
        this.streamService = streamService;
        this.calendarIcsService = calendarIcsService;
    }

    @GetMapping("/api/v3/events")
    public EventV3Page discoverEvents(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String format,
        @RequestParam(required = false) String categoryId,
        @RequestParam(required = false) Long minPrice,
        @RequestParam(required = false) Long maxPrice,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return eventV3Service.discoverEvents(q, format, categoryId, minPrice, maxPrice, page, size);
    }

    @GetMapping("/api/events/{id}/seats/best-available")
    public BestSeatsResult bestAvailableSeats(
        @PathVariable String id,
        @RequestParam String ticketTypeId,
        @RequestParam(defaultValue = "2") int quantity
    ) {
        UUID eventId = parseUuid(id);
        UUID typeId = parseUuid(ticketTypeId);
        return bestSeatsService.findBestContiguousSeats(eventId, typeId, quantity);
    }

    @GetMapping(value = "/api/events/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEventInventory(@PathVariable String id) {
        UUID eventId = parseUuid(id);
        return streamService.subscribe(eventId);
    }

    @GetMapping(value = "/api/events/{id}/calendar.ics", produces = "text/calendar; charset=utf-8")
    public ResponseEntity<byte[]> downloadCalendarIcs(@PathVariable String id) {
        UUID eventId = parseUuid(id);
        String ics = calendarIcsService.generateEventIcs(eventId);
        String filename = "eventflow-" + eventId.toString().substring(0, 8) + ".ics";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .header(HttpHeaders.CONTENT_TYPE, "text/calendar; charset=utf-8")
            .body(ics.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.invalid();
        }
    }

    @PostMapping("/api/organizer/events/{id}/format")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Map<String, Object> updateEventFormat(
        @PathVariable UUID id,
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody UpdateFormatRequest req
    ) {
        return eventV3Service.updateEventFormat(id, principal, req);
    }

    @PostMapping("/api/organizer/events/{id}/seat-matrix")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public SeatMatrixResult generateSeatMatrix(
        @PathVariable UUID id,
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody GenerateSeatMatrixRequest req
    ) {
        return eventV3Service.generateSeatMatrix(id, principal, req);
    }
}
