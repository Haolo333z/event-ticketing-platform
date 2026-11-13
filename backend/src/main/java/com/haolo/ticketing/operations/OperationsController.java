package com.haolo.ticketing.operations;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.bookings.GaBookingRequest;
import com.haolo.ticketing.shared.ApiException;

@RestController @RequestMapping("/api/admin/operations")
public class OperationsController {
    private final ReplayService service;private final RecoveryQuery recovery;
    public OperationsController(ReplayService service,RecoveryQuery recovery){this.service=service;this.recovery=recovery;}
    @GetMapping("/recovery") public RecoveryQuery.Page recovery(@AuthenticationPrincipal DemoPrincipal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String status){return recovery.list(principal,page,size,status==null?"OPEN":status);}
    @GetMapping("/events/{id}") public RecoveryQuery.Detail event(@AuthenticationPrincipal DemoPrincipal principal,@PathVariable String id){return recovery.event(principal,GaBookingRequest.uuid(id));}
    @GetMapping("/outbox") public ReplayService.Page list(@AuthenticationPrincipal DemoPrincipal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(principal,page,size);}
    @PostMapping("/events/{id}/replay") public ResponseEntity<ReplayService.Accepted> replay(@AuthenticationPrincipal DemoPrincipal principal,@PathVariable String id,@RequestBody Map<String,Object> body){
        if(body==null||!body.keySet().equals(Set.of("requestId","reason"))||!(body.get("reason") instanceof String reason))throw ApiException.invalid();
        return ResponseEntity.accepted().body(service.replay(principal,GaBookingRequest.uuid(id),GaBookingRequest.uuid(body.get("requestId")),reason));
    }
}
