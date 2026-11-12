package com.haolo.ticketing.bookings;

import java.util.*;

public sealed interface BookingRequest permits GaBookingRequest, SeatBookingRequest {
    UUID eventId();
    UUID ticketTypeId();
    int quantity();
    String hash();
    default String mode(){return this instanceof SeatBookingRequest ? "SEATED" : "GA";}
    static BookingRequest parse(Map<String,Object> body){
        return body!=null && body.containsKey("seatIds") ? SeatBookingRequest.parse(body) : GaBookingRequest.parse(body);
    }
}
