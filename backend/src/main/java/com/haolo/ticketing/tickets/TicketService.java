package com.haolo.ticketing.tickets;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import com.haolo.ticketing.bookings.BookingQueryService;
import com.haolo.ticketing.identity.DemoPrincipal;

@Service
public class TicketService {
    public record Ticket(UUID id,UUID bookingId,UUID bookingItemId,int ticketOrdinal,String code,UUID seatId,String seatLabel){}
    private final JdbcTemplate jdbc;
    private final BookingQueryService bookings;
    public TicketService(JdbcTemplate jdbc,BookingQueryService bookings){this.jdbc=jdbc;this.bookings=bookings;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void issue(UUID booking){
        if(!"CONFIRMED".equals(jdbc.queryForObject("SELECT status FROM ticketing.bookings WHERE id=?",String.class,booking)))throw new IllegalStateException("Only confirmed bookings issue tickets");
        var items=jdbc.queryForList("SELECT id,quantity FROM ticketing.booking_items WHERE booking_id=? ORDER BY id",booking);
        for(var item:items)for(int ordinal=1;ordinal<=((Number)item.get("quantity")).intValue();ordinal++){
            jdbc.update("INSERT INTO ticketing.tickets(id,booking_id,booking_item_id,ticket_ordinal,code) VALUES (?,?,?,?,?) ON CONFLICT(booking_item_id,ticket_ordinal) DO NOTHING",UUID.randomUUID(),booking,item.get("id"),ordinal,"EF-"+UUID.randomUUID());
        }
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Ticket> list(DemoPrincipal principal,UUID booking){
        var view=bookings.get(principal,booking);
        if(!view.status().equals("CONFIRMED"))return List.of();
        return jdbc.query("SELECT t.*,i.seat_id,s.label FROM ticketing.tickets t JOIN ticketing.booking_items i ON i.id=t.booking_item_id LEFT JOIN ticketing.seats s ON s.id=i.seat_id WHERE t.booking_id=? ORDER BY t.booking_item_id,t.ticket_ordinal",(r,n)->new Ticket(r.getObject("id",UUID.class),booking,r.getObject("booking_item_id",UUID.class),r.getInt("ticket_ordinal"),r.getString("code"),r.getObject("seat_id",UUID.class),r.getString("label")),booking);
    }
}
