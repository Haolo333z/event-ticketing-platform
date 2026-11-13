package com.haolo.ticketing;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.dao.DataIntegrityViolationException;
import com.haolo.ticketing.messaging.OutboxWriter;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.datasource.url=${PG_IT_URL}","expiry.enabled=false","messaging.enabled=false"})
@ActiveProfiles("test") @EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+")
class PostgresOutboxTest {
    @Autowired JdbcTemplate jdbc;@Autowired OutboxWriter writer;@Autowired PlatformTransactionManager manager;
    private final List<UUID> fixtureBookings=new ArrayList<>();
    UUID booking(){UUID id=UUID.randomUUID();fixtureBookings.add(id);jdbc.update("INSERT INTO ticketing.bookings(id,account_id,event_id,ticket_type_id,payment_intent_id,status,expires_at,total_amount_vnd,aggregate_version) VALUES (?,'10000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000002',?,'PENDING_PAYMENT',clock_timestamp()+interval '10m',100000,1)",id,UUID.randomUUID());return id;}
    @AfterEach void cleanupOwnFixtures(){
        // The commit test must keep its commit assertions, then remove only its own durable rows.
        for(UUID id:fixtureBookings){
            jdbc.update("DELETE FROM ticketing.payments WHERE booking_id=?",id);
            jdbc.update("DELETE FROM ticketing.outbox_events WHERE aggregate_id=?",id);
            jdbc.update("DELETE FROM ticketing.bookings WHERE id=?",id);
        }
    }
    Map<String,Object> payload(UUID id){return Map.of("bookingId",id.toString(),"expiresAt",jdbc.queryForObject("SELECT expires_at FROM ticketing.bookings WHERE id=?",(r,n)->r.getTimestamp(1).toInstant().toString(),id));}
    int count(String table,UUID id,String col){return jdbc.queryForObject("SELECT count(*) FROM ticketing."+table+" WHERE "+col+"=?",Integer.class,id);}
    @Test void businessAndOutboxCommitTogether(){
        var tx=new TransactionTemplate(manager);UUID[] id=new UUID[1];
        var event=tx.execute(s->{id[0]=booking();return writer.append(id[0],1,"BookingHeld",UUID.randomUUID(),payload(id[0]));});
        assertEquals(1,count("bookings",id[0],"id"));assertEquals(1,count("outbox_events",id[0],"aggregate_id"));
        assertEquals(1,event.schemaVersion());assertEquals(id[0],event.aggregateId());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM ticketing.outbox_events WHERE id=?",String.class,event.eventId()));
    }
    @Test void failuresAfterOutboxRollBackBusinessAndEvent(){
        var tx=new TransactionTemplate(manager);UUID[] id=new UUID[1];
        assertThrows(IllegalStateException.class,()->tx.execute(s->{id[0]=booking();writer.append(id[0],1,"BookingHeld",UUID.randomUUID(),payload(id[0]));throw new IllegalStateException("Failure after append");}));
        assertEquals(0,count("bookings",id[0],"id"));assertEquals(0,count("outbox_events",id[0],"aggregate_id"));
    }
    @Test void duplicateVersionsAndInvalidEnvelopesAreRejected(){
        var tx=new TransactionTemplate(manager);UUID[] id=new UUID[1];
        assertThrows(DataIntegrityViolationException.class,()->tx.execute(s->{id[0]=booking();writer.append(id[0],1,"BookingHeld",UUID.randomUUID(),payload(id[0]));writer.append(id[0],1,"BookingHeld",UUID.randomUUID(),payload(id[0]));return null;}));
        assertEquals(0,count("bookings",id[0],"id"));assertEquals(0,count("outbox_events",id[0],"aggregate_id"));
        assertThrows(IllegalTransactionStateException.class,()->writer.append(UUID.randomUUID(),1,"BookingHeld",UUID.randomUUID(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->writer.append(UUID.randomUUID(),1,"BookingHeld",UUID.randomUUID(),Map.of("bookingId","invalid","token","forbidden"))));
    }
    @Test void paymentLedgerConstraintsAreReal(){
        var tx=new TransactionTemplate(manager);
        assertThrows(DataIntegrityViolationException.class,()->tx.execute(s->{UUID id=booking();jdbc.update("INSERT INTO ticketing.payments(id,booking_id,request_id,scenario,status) VALUES (?,?,?,'SUCCESS','SUCCESS')",UUID.randomUUID(),id,UUID.randomUUID());return null;}));
        tx.execute(s->{UUID id=booking(),request=UUID.randomUUID();jdbc.update("INSERT INTO ticketing.payments(id,booking_id,request_id,scenario,status,recorded_at) VALUES (?,?,?,'SUCCESS','SUCCESS',clock_timestamp())",UUID.randomUUID(),id,request);s.setRollbackOnly();return null;});
    }
}
