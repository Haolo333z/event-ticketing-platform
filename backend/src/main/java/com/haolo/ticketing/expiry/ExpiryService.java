package com.haolo.ticketing.expiry;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.bookings.BookingTransactions;
import com.haolo.ticketing.bookings.BookingLifecycle;
import com.haolo.ticketing.payments.PaymentResultProcessor;
import com.haolo.ticketing.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ExpiryService {
    public enum Outcome { EXPIRED, NOT_DUE, BUSY, PROCESSED_PAYMENT }
    public record Pass(int candidates,int expired,int processedPayments,int busy,int failed){}
    private static final Logger log=LoggerFactory.getLogger(ExpiryService.class);
    private final JdbcTemplate jdbc;
    private final BookingTransactions transactions;
    private final BookingLifecycle lifecycle;
    private final PaymentResultProcessor processor;
    private final int batch;
    public ExpiryService(JdbcTemplate jdbc,BookingTransactions transactions,BookingLifecycle lifecycle,PaymentResultProcessor processor,
        @Value("${EXPIRY_BATCH_SIZE:50}") int batch,@Value("${EXPIRY_INTERVAL_SECONDS:5}") int interval){
        if(batch<1||batch>500||interval<1)throw new IllegalArgumentException("Invalid expiry policy");
        this.jdbc=jdbc;this.transactions=transactions;this.lifecycle=lifecycle;this.processor=processor;this.batch=batch;
    }
    public Outcome expire(UUID id){return transactions.run(()->{
        var rows=jdbc.queryForList("SELECT *,expires_at<=clock_timestamp() AS due FROM ticketing.bookings WHERE id=? FOR UPDATE SKIP LOCKED",id);
        if(rows.isEmpty())return Outcome.BUSY;
        var booking=rows.getFirst();
        if(!"PENDING_PAYMENT".equals(booking.get("status"))||!Boolean.TRUE.equals(booking.get("due")))return Outcome.NOT_DUE;
        processor.processLockedBooking(id);
        if(!"PENDING_PAYMENT".equals(lifecycle.lock(id).get("status")))return Outcome.PROCESSED_PAYMENT;
        lifecycle.releaseAndTransition(id,"EXPIRED",UUID.randomUUID());
        return Outcome.EXPIRED;
    });}
    public Pass runOnce(){
        // Candidate lookup holds no long batch transaction; every booking rechecks under its own lock.
        var ids=jdbc.queryForList("SELECT id FROM ticketing.bookings WHERE status='PENDING_PAYMENT' AND expires_at<=clock_timestamp() ORDER BY (EXISTS(SELECT 1 FROM ticketing.payments p WHERE p.booking_id=bookings.id AND p.recorded_at IS NOT NULL)),expires_at,id LIMIT ?",UUID.class,batch);
        int expired=0,processed=0,busy=0,failed=0;
        for(UUID id:ids)try{
            switch(expire(id)){case EXPIRED -> expired++;case PROCESSED_PAYMENT -> processed++;case BUSY -> busy++;default -> {}}
        }catch(ApiException ex){if(ex.status==503)busy++;else failed++;log.warn("Expiry transaction rolled back bookingId={} code={}",id,ex.code);}
        return new Pass(ids.size(),expired,processed,busy,failed);
    }
}
