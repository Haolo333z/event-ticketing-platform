package com.haolo.ticketing.inventory;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;

@Service
public class InventoryConfirmation {
    private final JdbcTemplate jdbc;
    public InventoryConfirmation(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void confirm(UUID booking){
        var items=jdbc.queryForList("SELECT seat_id,ticket_type_id,quantity FROM ticketing.booking_items WHERE booking_id=? ORDER BY seat_id",booking);
        if(items.isEmpty())throw new IllegalStateException("Booking has no items");
        for(var item:items){
            int changed;
            if(item.get("seat_id")==null)changed=jdbc.update("UPDATE ticketing.inventory_ga SET held_count=held_count-?,sold_count=sold_count+? WHERE ticket_type_id=? AND held_count>=?",item.get("quantity"),item.get("quantity"),item.get("ticket_type_id"),item.get("quantity"));
            else{
                jdbc.queryForList("SELECT id FROM ticketing.seats WHERE id=? FOR UPDATE",item.get("seat_id"));
                changed=jdbc.update("UPDATE ticketing.seats SET status='SOLD',hold_expires_at=NULL,version=version+1 WHERE id=? AND status='HELD' AND held_by_booking_id=?",item.get("seat_id"),booking);
            }
            if(changed!=1)throw new IllegalStateException("Confirmation requires the original inventory hold");
        }
    }
}
