package com.haolo.ticketing.messaging;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.haolo.ticketing.operations.TraceContext;
import com.haolo.ticketing.operations.TraceLog;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxWriter {
    public record Event(UUID eventId,String eventType,int schemaVersion,UUID aggregateId,long aggregateVersion,String occurredAt,UUID correlationId,Map<String,Object> payload){}
    private static final Map<String,Set<String>> FIELDS=Map.of(
        "BookingHeld",Set.of("bookingId","expiresAt"),"PaymentResultRecorded",Set.of("bookingId","paymentResultId"),
        "BookingConfirmed",Set.of("bookingId","confirmedAt"),"BookingPaymentFailed",Set.of("bookingId","failedAt"),
        "BookingCancelled",Set.of("bookingId","cancelledAt"),"BookingExpired",Set.of("bookingId","expiredAt"),
        "BookingRefundRequired",Set.of("bookingId","paymentId","compensationId"));
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public OutboxWriter(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    /** Caller commits business state + aggregate version + outbox in the SAME transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Event append(UUID bookingId,long version,String type,UUID correlationId,Map<String,Object> payload){
        if(bookingId==null||correlationId==null||version<1||!FIELDS.containsKey(type)||payload==null||!FIELDS.get(type).equals(payload.keySet()))throw new IllegalArgumentException("Invalid event envelope");
        for(var entry:payload.entrySet()){
            if(!(entry.getValue() instanceof String s))throw new IllegalArgumentException("Payload values must be strings");
            if(entry.getKey().endsWith("At")){if(!s.endsWith("Z"))throw new IllegalArgumentException("UTC required");Instant.parse(s);}
            else {UUID id=UUID.fromString(s);if(!id.toString().equalsIgnoreCase(s))throw new IllegalArgumentException("UUID required");}
        }
        if(!bookingId.toString().equals(payload.get("bookingId")))throw new IllegalArgumentException("Aggregate mismatch");
        Long stored=jdbc.queryForObject("SELECT aggregate_version FROM ticketing.bookings WHERE id=? FOR UPDATE",Long.class,bookingId);
        if(stored==null||stored!=version)throw new IllegalArgumentException("Aggregate version mismatch");
        var occurredAt=jdbc.queryForObject("SELECT clock_timestamp()",(r,n)->r.getTimestamp(1).toInstant());
        UUID eventId=UUID.randomUUID();
        jdbc.update("INSERT INTO ticketing.outbox_events(id,aggregate_id,aggregate_version,type,schema_version,payload,correlation_id,created_at) VALUES (?,?,?,?,1,CAST(? AS jsonb),?,?)",
            eventId,bookingId,version,type,mapper.writeValueAsString(payload),correlationId,java.sql.Timestamp.from(occurredAt));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){
                try(var context=TraceContext.open(correlationId,bookingId,eventId)){TraceLog.event("outbox_committed","COMMITTED",null);}
            }
        });
        return new Event(eventId,type,1,bookingId,version,occurredAt.toString(),correlationId,Map.copyOf(payload));
    }
}
