package com.haolo.ticketing.seatview;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.seatview.SeatViewModels.SeatViewDto;

@RestController
@RequestMapping("/api/events")
public class SeatViewController {

    private final SeatViewService seatViewService;

    public SeatViewController(SeatViewService seatViewService) {
        this.seatViewService = seatViewService;
    }

    @GetMapping("/{eventId}/seats/{seatId}/view")
    public ResponseEntity<SeatViewDto> getSeatView(
        @PathVariable UUID eventId,
        @PathVariable UUID seatId
    ) {
        return ResponseEntity.ok(seatViewService.getSeatView(eventId, seatId));
    }
}
