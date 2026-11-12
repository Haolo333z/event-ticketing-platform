package com.haolo.ticketing.payments;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.tickets.TicketService;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController
public class PaymentController {
    private final PaymentSimulatorService simulator;
    private final BookingCancelService cancel;
    private final TicketService tickets;
    public PaymentController(PaymentSimulatorService simulator,BookingCancelService cancel,TicketService tickets){this.simulator=simulator;this.cancel=cancel;this.tickets=tickets;}
    @PostMapping("/api/bookings/{id}/simulate-payment")
    public ResponseEntity<PaymentSimulatorService.Accepted> simulate(@AuthenticationPrincipal DemoPrincipal principal,@PathVariable String id,@RequestBody Map<String,Object> body,HttpServletRequest request){
        return ResponseEntity.accepted().body(simulator.simulate(principal,GaBookingRequest.uuid(id),SimulationInput.parse(body),correlation(request)));
    }
    @PostMapping("/api/bookings/{id}/cancel")
    public BookingQueryService.Booking cancel(@AuthenticationPrincipal DemoPrincipal principal,@PathVariable String id,HttpServletRequest request){return cancel.cancel(principal,GaBookingRequest.uuid(id),correlation(request));}
    @GetMapping("/api/bookings/{id}/tickets")
    public List<TicketService.Ticket> tickets(@AuthenticationPrincipal DemoPrincipal principal,@PathVariable String id){return tickets.list(principal,GaBookingRequest.uuid(id));}
    private static UUID correlation(HttpServletRequest request){return UUID.fromString(request.getAttribute("correlationId").toString());}
}
