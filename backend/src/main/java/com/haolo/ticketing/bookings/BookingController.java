package com.haolo.ticketing.bookings;

import java.net.URI;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController
public class BookingController {
    private final BookingCommandService commands;private final BookingQueryService queries;
    public BookingController(BookingCommandService commands,BookingQueryService queries){this.commands=commands;this.queries=queries;}
    @PostMapping("/api/bookings")
    public ResponseEntity<String> create(@AuthenticationPrincipal DemoPrincipal principal,@RequestHeader(name="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body,HttpServletRequest request){
        var result=commands.create(principal,key,BookingRequest.parse(body),UUID.fromString(request.getAttribute("correlationId").toString()));
        return ResponseEntity.accepted().location(URI.create("/api/bookings/"+result.bookingId())).contentType(MediaType.APPLICATION_JSON).body(result.body());
    }
    @GetMapping("/api/bookings/{id}")
    public BookingQueryService.Booking get(@AuthenticationPrincipal DemoPrincipal principal,@PathVariable String id){return queries.get(principal,GaBookingRequest.uuid(id));}
    @GetMapping("/api/me/bookings")
    public BookingQueryService.Page mine(@AuthenticationPrincipal DemoPrincipal principal,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return queries.mine(principal,page,size);}
}
