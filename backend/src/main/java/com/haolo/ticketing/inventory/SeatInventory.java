package com.haolo.ticketing.inventory;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.haolo.ticketing.bookings.SeatBookingRequest;
import com.haolo.ticketing.shared.ApiException;

@Service
public class SeatInventory {
    private final JdbcTemplate jdbc;
    public SeatInventory(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void reserve(UUID booking,SeatBookingRequest request,Timestamp expiresAt,long price){
        // The canonical request locks ONE immutable ID at a time, never relies on IN order.
        for(UUID id:request.seatIds()){
            var rows=jdbc.queryForList("SELECT *,hold_expires_at<=clock_timestamp() AS overdue FROM ticketing.seats WHERE id=? FOR UPDATE",id);
            if(rows.isEmpty())throw ApiException.invalid();
            var seat=rows.getFirst();
            if(!request.eventId().equals(seat.get("event_id"))||!request.ticketTypeId().equals(seat.get("ticket_type_id")))throw ApiException.invalid();
            if(!"AVAILABLE".equals(seat.get("status")))throw new ApiException(409,Boolean.TRUE.equals(seat.get("overdue"))?"HOLD_EXPIRY_PROCESSING":"INVENTORY_UNAVAILABLE","Ghế không còn trống; hãy tải lại sơ đồ ghế");
            int changed=jdbc.update("UPDATE ticketing.seats SET status='HELD',held_by_booking_id=?,hold_expires_at=?,version=version+1 WHERE id=? AND status='AVAILABLE'",booking,expiresAt,id);
            if(changed!=1)throw new IllegalStateException("Seat changed while locked");
            jdbc.update("INSERT INTO ticketing.booking_items(id,booking_id,event_id,ticket_type_id,seat_id,quantity,unit_price_vnd,total_price_vnd) VALUES (?,?,?,?,?,1,?,?)",UUID.randomUUID(),booking,request.eventId(),request.ticketTypeId(),id,price,price);
        }
    }
}
