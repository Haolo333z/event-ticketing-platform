package com.haolo.ticketing.messaging;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.payments.PaymentResultProcessor;

@Service
public class EventConsumer {
    public static final String PAYMENT="payment-v1",AUDIT="booking-audit-v1";
    private final JdbcTemplate jdbc;private final BookingTransactions tx;private final EventCodec codec;
    private final BookingLifecycle lifecycle;private final PaymentResultProcessor processor;
    public EventConsumer(JdbcTemplate jdbc,BookingTransactions tx,EventCodec codec,BookingLifecycle lifecycle,PaymentResultProcessor processor){this.jdbc=jdbc;this.tx=tx;this.codec=codec;this.lifecycle=lifecycle;this.processor=processor;}
    public void accept(String consumer,String key,String raw){
        if(!Set.of(PAYMENT,AUDIT).contains(consumer))throw new IllegalArgumentException("Unknown consumer");
        final OutboxWriter.Event event;
        try{event=codec.decode(raw,key);}catch(RuntimeException ex){throw new IllegalArgumentException("Invalid event envelope");}
        if(consumer.equals(PAYMENT)&&!event.eventType().equals("PaymentResultRecorded"))return;
        tx.run(()->{
            int inserted=jdbc.update("INSERT INTO ticketing.inbox_events(consumer,event_id,aggregate_id,aggregate_version,envelope_hash) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",consumer,event.eventId(),event.aggregateId(),event.aggregateVersion(),codec.hash(event));
            if(inserted==0){
                String hash=jdbc.queryForObject("SELECT envelope_hash FROM ticketing.inbox_events WHERE consumer=? AND event_id=?",String.class,consumer,event.eventId());
                if(!codec.hash(event).equals(hash))throw new IllegalArgumentException("Event ID reused with different content");
            }else {
                if(event.eventType().equals("PaymentResultRecorded")){
                    int linked=jdbc.queryForObject("SELECT count(*) FROM ticketing.payments WHERE id=? AND booking_id=? AND recorded_at IS NOT NULL AND status IN ('SUCCESS','FAILURE')",Integer.class,UUID.fromString((String)event.payload().get("paymentResultId")),event.aggregateId());
                    if(linked!=1)throw new IllegalArgumentException("Invalid durable payment result");
                }
                if(consumer.equals(PAYMENT)){
                lifecycle.lock(event.aggregateId());
                processor.processLockedBooking(event.aggregateId());
                }else{
                jdbc.update("INSERT INTO ticketing.event_projections(aggregate_id) VALUES (?) ON CONFLICT DO NOTHING",event.aggregateId());
                long last=jdbc.queryForObject("SELECT last_applied_version FROM ticketing.event_projections WHERE aggregate_id=? FOR UPDATE",Long.class,event.aggregateId());
                if(event.aggregateVersion()!=last+1)throw new IllegalStateException("Projection version gap or collision");
                if(event.eventType().equals("BookingRefundRequired")){
                    int linked=jdbc.queryForObject("SELECT count(*) FROM ticketing.payment_compensations WHERE id=? AND payment_id=? AND booking_id=?",Integer.class,UUID.fromString((String)event.payload().get("compensationId")),UUID.fromString((String)event.payload().get("paymentId")),event.aggregateId());
                    if(linked!=1)throw new IllegalArgumentException("Invalid compensation linkage");
                }
                jdbc.update("INSERT INTO ticketing.projection_events(aggregate_id,aggregate_version,event_id,event_type) VALUES (?,?,?,?)",event.aggregateId(),event.aggregateVersion(),event.eventId(),event.eventType());
                jdbc.update("UPDATE ticketing.event_projections SET last_applied_version=?,updated_at=clock_timestamp() WHERE aggregate_id=?",event.aggregateVersion(),event.aggregateId());
                }
            }
            jdbc.update("UPDATE ticketing.message_failures SET status='RESOLVED',resolved_at=clock_timestamp() WHERE consumer=? AND event_id=? AND status='DEAD'",consumer,event.eventId());
            return null;
        });
    }
}
