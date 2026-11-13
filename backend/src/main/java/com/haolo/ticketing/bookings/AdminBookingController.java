package com.haolo.ticketing.bookings;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController @RequestMapping("/api/admin/bookings")
public class AdminBookingController {
    private final BookingQueryService query;public AdminBookingController(BookingQueryService query){this.query=query;}
    @GetMapping public BookingQueryService.Page list(@AuthenticationPrincipal DemoPrincipal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String status){return query.admin(principal,page,size,status);}
}
