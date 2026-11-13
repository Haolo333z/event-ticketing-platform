package com.haolo.ticketing;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=${PG_IT_URL}","expiry.enabled=true","EXPIRY_INTERVAL_SECONDS=1","BOOKING_HOLD_TTL_SECONDS=2",
    "messaging.enabled=false","operations.metrics.snapshot-ttl-ms=0"})
@ActiveProfiles("test") @DirtiesContext @EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+")
class PostgresMetricsTest {
    @Autowired JdbcTemplate jdbc;@Autowired MeterRegistry registry;@Autowired BookingCommandService commands;
    @Autowired BookingCancelService cancel;@Autowired ThreadPoolTaskScheduler scheduler;@Autowired PlatformTransactionManager manager;
    @Autowired com.haolo.ticketing.payments.PaymentSimulatorService simulator;@Autowired com.haolo.ticketing.expiry.ExpiryService expiry;
    @Autowired ObjectMapper json;@LocalServerPort int port;
    final HttpClient http=HttpClient.newHttpClient();
    final DemoPrincipal owner=new DemoPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"),"CUSTOMER");
    UUID event,ga,seated,seat;final List<UUID> failures=new ArrayList<>();
    static final List<Map<String,Object>> evidence=new ArrayList<>();
    void await(BooleanSupplier condition){long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<end){if(condition.getAsBoolean())return;try{Thread.sleep(50);}catch(InterruptedException ex){throw new AssertionError(ex);}}assertTrue(condition.getAsBoolean(),"Metrics/worker condition timed out");}
    @BeforeEach void fixture(){
        scheduler.stop();await(()->scheduler.getActiveCount()==0);assertFalse(scheduler.isRunning());
        event=UUID.randomUUID();ga=UUID.randomUUID();seated=UUID.randomUUID();seat=UUID.randomUUID();
        jdbc.update("INSERT INTO ticketing.events(id,title,description,venue,sale_start_at,sale_end_at,starts_at,status) VALUES (?,'Metrics fixture','','Local',clock_timestamp()-interval '1h',clock_timestamp()+interval '1h',clock_timestamp()+interval '2h','PUBLISHED')",event);
        jdbc.update("INSERT INTO ticketing.ticket_types(id,event_id,name,mode,price_vnd) VALUES (?,?,'GA','GA',100000),(?,?,'Seat','SEATED',100000)",ga,event,seated,event);
        jdbc.update("INSERT INTO ticketing.inventory_ga(ticket_type_id,capacity) VALUES (?,6)",ga);
        jdbc.update("INSERT INTO ticketing.seats(id,event_id,ticket_type_id,label) VALUES (?,?,?,'M1')",seat,event,seated);
    }
    @AfterEach void cleanup(){
        scheduler.stop();await(()->scheduler.getActiveCount()==0);
        for(UUID id:failures)jdbc.update("DELETE FROM ticketing.message_failures WHERE id=?",id);
        jdbc.update("DELETE FROM ticketing.compensation_attempts WHERE compensation_id IN(SELECT id FROM ticketing.payment_compensations WHERE booking_id IN(SELECT id FROM ticketing.bookings WHERE event_id=?))",event);
        for(String table:List.of("tickets","payment_compensations","payments","idempotency_keys","booking_status_history","outbox_events"))jdbc.update("DELETE FROM ticketing."+table+" WHERE "+(table.equals("outbox_events")?"aggregate_id":"booking_id")+" IN(SELECT id FROM ticketing.bookings WHERE event_id=?)",event);
        jdbc.update("DELETE FROM ticketing.booking_items WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.seats WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.bookings WHERE event_id=?",event);
        jdbc.update("DELETE FROM ticketing.inventory_ga WHERE ticket_type_id=?",ga);jdbc.update("DELETE FROM ticketing.ticket_types WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.events WHERE id=?",event);
    }
    UUID book(boolean useSeat){return commands.create(owner,"metrics-"+UUID.randomUUID(),useSeat?new SeatBookingRequest(event,seated,List.of(seat)):new GaBookingRequest(event,ga,1),UUID.randomUUID()).bookingId();}
    double gauge(String name,String... tags){var gauge=registry.find(name).tags(tags).gauge();assertNotNull(gauge,name);return gauge.value();}
    double db(String sql,Object...args){return jdbc.queryForObject(sql,Double.class,args);}
    HttpResponse<String> get(String path,String token)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    double exposedGauge(String name)throws Exception{
        var response=get("/actuator/metrics/"+name,"demo-admin-token");assertEquals(200,response.statusCode(),response.body());var n=json.readTree(response.body());return n.path("measurements").get(0).path("value").doubleValue();
    }
    void assertStates(){for(String status:List.of("PENDING_PAYMENT","CONFIRMED","PAYMENT_FAILED","CANCELLED","EXPIRED","REFUND_REQUIRED"))assertEquals(db("SELECT count(*) FROM ticketing.bookings WHERE status=?",status),gauge("ticketing.bookings","status",status));}
    Map<String,Double> stateCounts(){var counts=new LinkedHashMap<String,Double>();for(String status:List.of("PENDING_PAYMENT","CONFIRMED","PAYMENT_FAILED","CANCELLED","EXPIRED","REFUND_REQUIRED"))counts.put(status,db("SELECT count(*) FROM ticketing.bookings WHERE status=?",status));return counts;}
    UUID stableBooking(){UUID id=book(false);jdbc.update("UPDATE ticketing.bookings SET expires_at=clock_timestamp()+interval '60s' WHERE id=?",id);return id;}
    void expireFixture(UUID booking){jdbc.update("UPDATE ticketing.bookings SET created_at=clock_timestamp()-interval '10s',expires_at=clock_timestamp()-interval '1s' WHERE id=?",booking);expiry.expire(booking);}
    double sqlOverdue(){return db("SELECT count(*) FROM ticketing.bookings WHERE status='PENDING_PAYMENT' AND expires_at<=statement_timestamp()");}
    @Test void stoppedWorkerShowsOverdueGrowthAndScheduledResumeClearsReleasedHolds()throws Exception{
        double before=exposedGauge("ticketing.holds.overdue");UUID gaBooking=book(false),seatBooking=book(true);assertStates();
        await(()->db("SELECT count(*) FROM ticketing.bookings WHERE id IN (?,?) AND expires_at<=statement_timestamp()",gaBooking,seatBooking)==2);
        double overdue=exposedGauge("ticketing.holds.overdue"),oldest=exposedGauge("ticketing.holds.overdue.oldest.age");
        assertEquals(sqlOverdue(),overdue);assertTrue(overdue>=before+2);assertTrue(oldest>0);assertFalse(scheduler.isRunning());
        await(()->gauge("ticketing.holds.overdue.oldest.age")>oldest+0.6);
        double older=exposedGauge("ticketing.holds.overdue.oldest.age");assertTrue(older>oldest);
        assertEquals(2,db("SELECT count(*) FROM ticketing.bookings WHERE id IN (?,?) AND status='PENDING_PAYMENT'",gaBooking,seatBooking));
        scheduler.start();assertTrue(scheduler.isRunning());
        await(()->db("SELECT count(*) FROM ticketing.bookings WHERE id IN (?,?) AND status='EXPIRED'",gaBooking,seatBooking)==2);
        scheduler.stop();await(()->scheduler.getActiveCount()==0);
        double after=exposedGauge("ticketing.holds.overdue");assertEquals(sqlOverdue(),after);assertTrue(after<overdue);assertStates();
        double ageAfter=exposedGauge("ticketing.holds.overdue.oldest.age");if(after==0)assertEquals(0,ageAfter);
        assertEquals(0,db("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",ga));
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM ticketing.seats WHERE id=?",String.class,seat));
        evidence.add(Map.of("case","actual scheduler stopped/resumed, real GA and seated holds","beforeOverdue",before,"pausedOverdue",overdue,"pausedOldestSeconds",oldest,"laterOldestSeconds",older,"resumedOverdue",after,"resumedOldestSeconds",ageAfter,"releasedOwnBookings",2,"result","PASS"));
    }
    @Test void stateGaugesSeeCommittedStateAndDoNotPublishUncommittedOrRolledBackChanges(){
        var before=stateCounts();UUID booking=stableBooking();UUID cancelledFixture=stableBooking();cancel.cancel(owner,cancelledFixture,UUID.randomUUID());
        UUID confirmed=stableBooking();simulator.simulate(owner,confirmed,new com.haolo.ticketing.payments.SimulationInput("SUCCESS",UUID.randomUUID()),UUID.randomUUID());
        UUID failed=stableBooking();simulator.simulate(owner,failed,new com.haolo.ticketing.payments.SimulationInput("FAILURE",UUID.randomUUID()),UUID.randomUUID());
        UUID expired=stableBooking();expireFixture(expired);
        UUID refund=stableBooking();expireFixture(refund);simulator.simulate(owner,refund,new com.haolo.ticketing.payments.SimulationInput("SUCCESS",UUID.randomUUID()),UUID.randomUUID());
        assertStates();for(var row:before.entrySet())assertEquals(row.getValue()+1,gauge("ticketing.bookings","status",row.getKey()));
        double pending=gauge("ticketing.bookings","status","PENDING_PAYMENT"),cancelled=gauge("ticketing.bookings","status","CANCELLED");
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(s->{
            jdbc.update("UPDATE ticketing.bookings SET status='CANCELLED' WHERE id=?",booking);
            assertEquals(pending,gauge("ticketing.bookings","status","PENDING_PAYMENT"));assertEquals(cancelled,gauge("ticketing.bookings","status","CANCELLED"));
            throw new IllegalStateException("Rollback metric fixture");
        }));assertStates();
        cancel.cancel(owner,booking,UUID.randomUUID());assertStates();assertEquals(pending-1,gauge("ticketing.bookings","status","PENDING_PAYMENT"));assertEquals(cancelled+1,gauge("ticketing.bookings","status","CANCELLED"));
        evidence.add(Map.of("case","all six booking states via real services; committed-only snapshot and rollback","addedPerState",1,"states",before.keySet(),"result","PASS"));
    }
    @Test void outboxAgeIncludesFailedRowsAndChangesAfterPublication()throws Exception{
        UUID booking=book(false);UUID id=jdbc.queryForObject("SELECT id FROM ticketing.outbox_events WHERE aggregate_id=?",UUID.class,booking);
        jdbc.update("UPDATE ticketing.outbox_events SET created_at=statement_timestamp()-interval '20s',status='FAILED',attempts=3 WHERE id=?",id);
        double age=exposedGauge("ticketing.outbox.unpublished.oldest.age");assertTrue(age>=20);
        double expected=db("SELECT coalesce(greatest(extract(epoch FROM statement_timestamp()-min(created_at)),0),0) FROM ticketing.outbox_events WHERE status IN ('PENDING','FAILED')");assertEquals(expected,age,0.5);
        assertEquals(db("SELECT count(*) FROM ticketing.outbox_events WHERE status='FAILED'"),gauge("ticketing.outbox.messages","status","FAILED"));
        assertEquals(db("SELECT coalesce(sum(attempts),0) FROM ticketing.outbox_events"),gauge("ticketing.outbox.attempts"));
        jdbc.update("UPDATE ticketing.outbox_events SET status='PUBLISHED',published_at=statement_timestamp() WHERE id=?",id);
        double after=exposedGauge("ticketing.outbox.unpublished.oldest.age");
        assertEquals(db("SELECT coalesce(greatest(extract(epoch FROM statement_timestamp()-min(created_at)),0),0) FROM ticketing.outbox_events WHERE status IN ('PENDING','FAILED')"),after,0.5);
        evidence.add(Map.of("case","FAILED outbox included; publication refreshes age","beforeSeconds",age,"afterSeconds",after,"result","PASS"));
    }
    @Test void durableRecoveryAndDlqStatesHaveFiniteLabelsAndRefresh(){
        double pending=gauge("ticketing.messaging.dlq.messages","state","PENDING"),exhausted=gauge("ticketing.messaging.dlq.messages","state","EXHAUSTED"),published=gauge("ticketing.messaging.dlq.messages","state","PUBLISHED");
        UUID id=UUID.randomUUID();failures.add(id);
        jdbc.update("INSERT INTO ticketing.message_failures(id,consumer,topic,partition_id,record_offset,attempts,error) VALUES (?,'payment-v1',?,0,1,3,'Fixture')",id,"metrics-"+id);
        assertEquals(pending+1,gauge("ticketing.messaging.dlq.messages","state","PENDING"));
        assertEquals(db("SELECT count(*) FROM ticketing.message_failures WHERE consumer='payment-v1' AND status='DEAD'"),gauge("ticketing.messaging.failures","consumer","payment-v1","status","DEAD"));
        jdbc.update("UPDATE ticketing.message_failures SET dlq_attempts=3 WHERE id=?",id);
        assertEquals(exhausted+1,gauge("ticketing.messaging.dlq.messages","state","EXHAUSTED"));assertEquals(pending,gauge("ticketing.messaging.dlq.messages","state","PENDING"));
        jdbc.update("UPDATE ticketing.message_failures SET dlq_published_at=statement_timestamp(),status='RESOLVED',resolved_at=statement_timestamp() WHERE id=?",id);
        assertEquals(published+1,gauge("ticketing.messaging.dlq.messages","state","PUBLISHED"));assertEquals(exhausted,gauge("ticketing.messaging.dlq.messages","state","EXHAUSTED"));
        assertEquals(db("SELECT count(*) FROM ticketing.message_failures WHERE consumer='payment-v1' AND status='RESOLVED'"),gauge("ticketing.messaging.failures","consumer","payment-v1","status","RESOLVED"));
    }
    @Test void adminOnlyMetricsAndApiLatencyUseTemplatesWithoutPrivateLabels()throws Exception{
        assertEquals(401,get("/actuator/metrics",null).statusCode());assertEquals(403,get("/actuator/metrics","demo-customer-token").statusCode());assertEquals(401,get("/actuator/metrics","unknown").statusCode());
        assertEquals(200,get("/actuator/metrics","demo-admin-token").statusCode());assertEquals(200,get("/actuator/metrics/ticketing.bookings","demo-admin-token").statusCode());
        double before=registry.find("http.server.requests").tags("uri","/api/events/{id}","method","GET","status","200").timers().stream().mapToLong(Timer::count).sum();
        String marker="private-metric-"+UUID.randomUUID();
        for(int i=0;i<2;i++)assertEquals(200,get("/api/events/"+event+"?token="+marker,null).statusCode());
        assertEquals(404,get("/api/events/"+UUID.randomUUID(),null).statusCode());get("/unknown/"+marker,null);
        await(()->registry.find("http.server.requests").tags("uri","/api/events/{id}","method","GET","status","200").timers().stream().mapToLong(Timer::count).sum()==before+2);
        var timer=registry.find("http.server.requests").tags("uri","/api/events/{id}","method","GET","status","200").timer();assertNotNull(timer);
        assertTrue(timer.totalTime(TimeUnit.SECONDS)>0);assertTrue(timer.max(TimeUnit.SECONDS)>0);
        var response=get("/actuator/metrics/http.server.requests?tag=uri:%2Fapi%2Fevents%2F%7Bid%7D&tag=status:200&tag=method:GET","demo-admin-token");assertEquals(200,response.statusCode());
        var latency=json.readTree(response.body());assertEquals("seconds",latency.path("baseUnit").asString());assertTrue(response.body().contains("TOTAL_TIME"));assertTrue(response.body().contains("MAX"));
        for(var meter:registry.getMeters())if(meter.getId().getName().startsWith("ticketing.")||meter.getId().getName().equals("http.server.requests"))for(var tag:meter.getId().getTags()){
            assertFalse(Set.of("bookingId","eventId","correlationId","userId","token","accountId").contains(tag.getKey()));
            assertFalse(tag.getValue().contains(marker));assertFalse(tag.getValue().contains(event.toString()));assertFalse(tag.getValue().contains("demo-"));
            assertFalse(tag.getValue().matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*"));
        }
        evidence.add(Map.of("case","HTTP latency + admin-only metrics + bounded private-free tags","addedSuccessfulRequests",2,"totalTimeSeconds",timer.totalTime(TimeUnit.SECONDS),"maxSeconds",timer.max(TimeUnit.SECONDS),"result","PASS"));
    }
    @AfterAll static void save()throws Exception{Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target/part82-metrics-evidence.json"),new ObjectMapper().writeValueAsString(evidence));}
}
