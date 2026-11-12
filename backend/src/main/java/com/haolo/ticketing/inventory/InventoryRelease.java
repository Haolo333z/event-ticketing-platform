package com.haolo.ticketing.inventory;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Caller owns the booking lock and rechecks its state before releasing. */
@Service
public class InventoryRelease {
    private final JdbcTemplate jdbc;
    public InventoryRelease(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void release(UUID booking){
        var items=jdbc.queryForList("SELECT seat_id,ticket_type_id,quantity FROM ticketing.booking_items WHERE booking_id=? ORDER BY seat_id",booking);
        if(items.isEmpty())throw new IllegalStateException("Booking has no inventory items");
        for(var item:items){
            if(item.get("seat_id")==null){
                int changed=jdbc.update("UPDATE ticketing.inventory_ga SET held_count=held_count-? WHERE ticket_type_id=? AND held_count>=?",item.get("quantity"),item.get("ticket_type_id"),item.get("quantity"));
                if(changed!=1)throw new IllegalStateException("GA hold inconsistent");
            }else{
                UUID seat=(UUID)item.get("seat_id");
                jdbc.queryForList("SELECT id FROM ticketing.seats WHERE id=? FOR UPDATE",seat);
                int changed=jdbc.update("UPDATE ticketing.seats SET status='AVAILABLE',held_by_booking_id=NULL,hold_expires_at=NULL,version=version+1 WHERE id=? AND status='HELD' AND held_by_booking_id=?",seat,booking);
                if(changed!=1)throw new IllegalStateException("Seat owner inconsistent");
            }
        }
    }
}
