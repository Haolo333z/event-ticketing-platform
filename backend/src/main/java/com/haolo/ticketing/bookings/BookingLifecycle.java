package com.haolo.ticketing.bookings;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import com.haolo.ticketing.inventory.InventoryRelease;
import com.haolo.ticketing.messaging.OutboxWriter;
import com.haolo.ticketing.shared.ApiException;

/** Internal transaction contract: booking lock precedes inventory locks and state/history/outbox. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class BookingLifecycle {
    private final JdbcTemplate jdbc;
    private final InventoryRelease inventory;
    private final OutboxWriter outbox;
    private static final Map<String,String> EVENTS=Map.of("CONFIRMED","BookingConfirmed","PAYMENT_FAILED","BookingPaymentFailed","CANCELLED","BookingCancelled","EXPIRED","BookingExpired","REFUND_REQUIRED","BookingRefundRequired");
    private static final Map<String,String> TIMES=Map.of("CONFIRMED","confirmedAt","PAYMENT_FAILED","failedAt","CANCELLED","cancelledAt","EXPIRED","expiredAt");
    public BookingLifecycle(JdbcTemplate jdbc,InventoryRelease inventory,OutboxWriter outbox){this.jdbc=jdbc;this.inventory=inventory;this.outbox=outbox;}
    public Map<String,Object> lock(UUID id){
        var rows=jdbc.queryForList("SELECT *,expires_at<=clock_timestamp() AS due FROM ticketing.bookings WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    public void releaseAndTransition(UUID id,String target,UUID correlation){inventory.release(id);transition(id,target,correlation,Map.of());}
    public void transition(UUID id,String target,UUID correlation,Map<String,Object> extra){
        var booking=lock(id);String from=(String)booking.get("status");
        boolean valid=from.equals("PENDING_PAYMENT")&&Set.of("CONFIRMED","PAYMENT_FAILED","CANCELLED","EXPIRED").contains(target)||from.equals("EXPIRED")&&target.equals("REFUND_REQUIRED");
        if(!valid)throw new IllegalStateException("Invalid booking transition "+from+" -> "+target);
        Timestamp now=jdbc.queryForObject("SELECT clock_timestamp()",(r,n)->r.getTimestamp(1));
        Long version=jdbc.queryForObject("UPDATE ticketing.bookings SET status=?,expired_at=CASE WHEN ?='EXPIRED' THEN ? ELSE expired_at END,aggregate_version=aggregate_version+1 WHERE id=? RETURNING aggregate_version",Long.class,target,target,now,id);
        var payload=new HashMap<String,Object>(extra);payload.put("bookingId",id.toString());
        if(TIMES.containsKey(target))payload.put(TIMES.get(target),now.toInstant().toString());
        var event=outbox.append(id,version,EVENTS.get(target),correlation,payload);
        jdbc.update("INSERT INTO ticketing.booking_status_history(id,booking_id,from_status,to_status,occurred_at,cause_event_id) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,from,target,now,event.eventId());
    }
}
