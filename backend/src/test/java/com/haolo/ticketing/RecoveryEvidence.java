package com.haolo.ticketing;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Opt-in T9.3 evidence: one committed SQL snapshot, IDs/counters only, never raw messages/payment payloads. */
final class RecoveryEvidence {
    static void capture(JdbcTemplate jdbc,String scenario,String phase,UUID event) {
        String directory=System.getenv("RECOVERY_EVIDENCE_DIR");
        if(directory==null||directory.isBlank())return;
        if(!(scenario+"-"+phase).matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException("Evidence name");
        String sql="""
            WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='%s'::uuid),
            i AS (SELECT * FROM ticketing.booking_items WHERE event_id='%s'::uuid),
            s AS (SELECT * FROM ticketing.seats WHERE event_id='%s'::uuid),
            g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='%s'::uuid),
            o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
            p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
            c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
            SELECT jsonb_build_object(
              'dbTime',statement_timestamp(),
              'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
              'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
              'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
              'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
              'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
              'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
              'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
              'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
              'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
              'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
              'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
              'violations',jsonb_build_object(
                'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
                'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
                'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
                'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
                'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
                'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
                'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
                'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
                'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
                'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
                'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
              ))::text;
            """.formatted(event,event,event,event);
        try {
            var mapper=new ObjectMapper();
            String result=jdbc.queryForObject(sql,String.class);
            var snapshot=mapper.readTree(result);
            var root=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(root);
            String name=scenario+"--"+phase;
            Files.writeString(root.resolve(name+".sql"),"-- Read-only snapshot; original DB exists only during the isolated harness.\n"+sql,StandardCharsets.UTF_8);
            var record=new LinkedHashMap<String,Object>();
            record.put("scenario",scenario);record.put("phase",phase);record.put("eventId",event.toString());record.put("capturedAt",Instant.now().toString());
            record.put("database",jdbc.queryForObject("SELECT current_database()",String.class));
            record.put("snapshot",snapshot);
            Files.writeString(root.resolve(name+".json"),mapper.writeValueAsString(record),StandardCharsets.UTF_8);
            for(var violation:snapshot.get("violations"))if(violation.intValue()!=0)throw new AssertionError("Recovery SQL invariant failed at "+name);
        }catch(java.io.IOException ex){throw new IllegalStateException("Cannot write recovery evidence",ex);}
    }
}
