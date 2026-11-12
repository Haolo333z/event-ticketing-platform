package com.haolo.ticketing.expiry;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.bookings.BookingTransactions;
import com.haolo.ticketing.inventory.InventoryRelease;
import com.haolo.ticketing.messaging.OutboxWriter;
import com.haolo.ticketing.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ExpiryService {
    public enum Outcome { EXPIRED, NOT_DUE, BUSY, DEFERRED_PAYMENT }
    public record Pass(int candidates,int expired,int deferred,int busy,int failed){}
    private static final Logger log=LoggerFactory.getLogger(ExpiryService.class);
    private final JdbcTemplate jdbc;
    private final BookingTransactions transactions;
    private final InventoryRelease inventory;
    private final OutboxWriter outbox;
    private final int batch;
    public ExpiryService(JdbcTemplate jdbc,BookingTransactions transactions,InventoryRelease inventory,OutboxWriter outbox,
        @Value("${EXPIRY_BATCH_SIZE:50}") int batch,@Value("${EXPIRY_INTERVAL_SECONDS:5}") int interval){
        if(batch<1||batch>500||interval<1)throw new IllegalArgumentException("Invalid expiry policy");
        this.jdbc=jdbc;this.transactions=transactions;this.inventory=inventory;this.outbox=outbox;this.batch=batch;
    }
    public Outcome expire(UUID id){return transactions.run(()->{
        var rows=jdbc.queryForList("SELECT *,expires_at<=clock_timestamp() AS due FROM ticketing.bookings WHERE id=? FOR UPDATE SKIP LOCKED",id);
        if(rows.isEmpty())return Outcome.BUSY;
        var booking=rows.getFirst();
        if(!"PENDING_PAYMENT".equals(booking.get("status"))||!Boolean.TRUE.equals(booking.get("due")))return Outcome.NOT_DUE;
        // T5.2 will invoke the shared payment processor here. Never release a durable result blindly.
        if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ticketing.payments WHERE booking_id=? AND recorded_at IS NOT NULL)",Boolean.class,id))return Outcome.DEFERRED_PAYMENT;
        inventory.release(id);
        var result=jdbc.queryForMap("UPDATE ticketing.bookings SET status='EXPIRED',expired_at=clock_timestamp(),aggregate_version=aggregate_version+1 WHERE id=? AND status='PENDING_PAYMENT' RETURNING expired_at,aggregate_version",id);
        String at=((java.sql.Timestamp)result.get("expired_at")).toInstant().toString();
        long version=((Number)result.get("aggregate_version")).longValue();
        var event=outbox.append(id,version,"BookingExpired",UUID.randomUUID(),Map.of("bookingId",id.toString(),"expiredAt",at));
        jdbc.update("INSERT INTO ticketing.booking_status_history(id,booking_id,from_status,to_status,occurred_at,cause_event_id) VALUES (?,?,'PENDING_PAYMENT','EXPIRED',?,?)",UUID.randomUUID(),id,result.get("expired_at"),event.eventId());
        return Outcome.EXPIRED;
    });}
    public Pass runOnce(){
        // Prioritize ordinary expiry over deferred payment rows, without a long batch transaction.
        var ids=jdbc.queryForList("SELECT id FROM ticketing.bookings WHERE status='PENDING_PAYMENT' AND expires_at<=clock_timestamp() ORDER BY (EXISTS(SELECT 1 FROM ticketing.payments p WHERE p.booking_id=bookings.id AND p.recorded_at IS NOT NULL)),expires_at,id LIMIT ?",UUID.class,batch);
        int expired=0,deferred=0,busy=0,failed=0;
        for(UUID id:ids)try{
            switch(expire(id)){case EXPIRED -> expired++;case DEFERRED_PAYMENT -> deferred++;case BUSY -> busy++;default -> {}}
        }catch(ApiException ex){if(ex.status==503)busy++;else failed++;log.warn("Expiry transaction rolled back bookingId={} code={}",id,ex.code);}
        return new Pass(ids.size(),expired,deferred,busy,failed);
    }
}
