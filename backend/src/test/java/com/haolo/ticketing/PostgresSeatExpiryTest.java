package com.haolo.ticketing;

import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.expiry.ExpiryService;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=${PG_IT_URL}","expiry.enabled=false","messaging.enabled=false"})
@ActiveProfiles("test") @EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+")
class PostgresSeatExpiryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingCommandService commands;
    @Autowired BookingTransactions transactions;
    @Autowired ExpiryService expiry;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.context.ApplicationContext context;
    @LocalServerPort int port;
    UUID event,type,ga,other;
    List<UUID> seats;
    final DemoPrincipal owner=new DemoPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"),"CUSTOMER");
    DemoPrincipal customer;
    final HttpClient http=HttpClient.newHttpClient();
    static final List<Map<String,Object>> evidence=new ArrayList<>();
    @BeforeEach void fixture(){
        assertTrue(context.getBeansOfType(com.haolo.ticketing.expiry.ExpiryScheduler.class).isEmpty(),"Scheduler must not race controlled expiry tests, even if the local environment enables it");
        event=UUID.randomUUID();type=UUID.randomUUID();ga=UUID.randomUUID();other=UUID.randomUUID();customer=new DemoPrincipal(other,"CUSTOMER");
        jdbc.update("INSERT INTO ticketing.users(id,demo_role,display_name) VALUES (?,'CUSTOMER','Seat competitor')",other);
        jdbc.update("INSERT INTO ticketing.events(id,title,description,venue,sale_start_at,sale_end_at,starts_at,status) VALUES (?,'Seat test','','Local',clock_timestamp()-interval '1h',clock_timestamp()+interval '1h',clock_timestamp()+interval '2h','PUBLISHED')",event);
        jdbc.update("INSERT INTO ticketing.ticket_types(id,event_id,name,mode,price_vnd) VALUES (?,?,'Seats','SEATED',200000),(?,?,'GA','GA',70000)",type,event,ga,event);
        jdbc.update("INSERT INTO ticketing.inventory_ga(ticket_type_id,capacity) VALUES (?,4)",ga);
        seats=new ArrayList<>();for(int i=0;i<4;i++)seats.add(UUID.randomUUID());seats.sort(Comparator.comparing(UUID::toString));
        for(int i=0;i<4;i++)jdbc.update("INSERT INTO ticketing.seats(id,event_id,ticket_type_id,label) VALUES (?,?,?,?)",seats.get(i),event,type,"A"+(i+1));
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM ticketing.bookings WHERE event_id=?)",event);
        jdbc.update("DELETE FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM ticketing.bookings WHERE event_id=?)",event);
        jdbc.update("DELETE FROM ticketing.payments WHERE booking_id IN (SELECT id FROM ticketing.bookings WHERE event_id=?)",event);
        for(String table:List.of("idempotency_keys","booking_status_history","outbox_events")){
            String col=table.equals("outbox_events")?"aggregate_id":"booking_id";
            jdbc.update("DELETE FROM ticketing."+table+" WHERE "+col+" IN (SELECT id FROM ticketing.bookings WHERE event_id=?)",event);
        }
        jdbc.update("DELETE FROM ticketing.booking_items WHERE event_id=?",event);
        jdbc.update("DELETE FROM ticketing.seats WHERE event_id=?",event);
        jdbc.update("DELETE FROM ticketing.bookings WHERE event_id=?",event);
        jdbc.update("DELETE FROM ticketing.inventory_ga WHERE ticket_type_id=?",ga);
        jdbc.update("DELETE FROM ticketing.ticket_types WHERE event_id=?",event);
        jdbc.update("DELETE FROM ticketing.events WHERE id=?",event);
        jdbc.update("DELETE FROM ticketing.users WHERE id=?",other);
    }
    SeatBookingRequest request(UUID... ids){return new SeatBookingRequest(event,type,List.of(ids));}
    BookingCommandService.Result book(String key,UUID... ids){return commands.create(owner,key,request(ids),UUID.randomUUID());}
    HttpResponse<String> post(String key,Object payload) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/bookings"))
            .header("Authorization","Bearer demo-customer-token").header("Content-Type","application/json").header("Idempotency-Key",key)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofString());
    }
    int count(String table){String col=table.equals("outbox_events")?"aggregate_id":"booking_id";return jdbc.queryForObject("SELECT count(*) FROM ticketing."+table+" WHERE "+col+" IN (SELECT id FROM ticketing.bookings WHERE event_id=?)",Integer.class,event);}
    int bookings(){return jdbc.queryForObject("SELECT count(*) FROM ticketing.bookings WHERE event_id=?",Integer.class,event);}
    int heldSeats(){return jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE event_id=? AND status='HELD'",Integer.class,event);}
    String status(UUID booking){return jdbc.queryForObject("SELECT status FROM ticketing.bookings WHERE id=?",String.class,booking);}
    void due(UUID booking){
        // Fixture moves the DB deadline, not wall clock or production code; preserve expires>created.
        jdbc.update("UPDATE ticketing.bookings SET created_at=clock_timestamp()-interval '20m',expires_at=clock_timestamp()-interval '1s' WHERE id=?",booking);
        jdbc.update("UPDATE ticketing.seats SET hold_expires_at=(SELECT expires_at FROM ticketing.bookings WHERE id=?) WHERE held_by_booking_id=?",booking,booking);
    }
    Connection connection() throws Exception{return DriverManager.getConnection(System.getenv("PG_IT_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"));}
    void assertCode(int httpStatus,String code,Runnable action){var ex=assertThrows(ApiException.class,action::run);assertEquals(httpStatus,ex.status);assertEquals(code,ex.code);}
    @Test void mapAndOwnerConstraintsAreEnforced() throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/events/"+event+"/availability")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());assertTrue(response.body().contains("A1"));assertTrue(response.body().contains("AVAILABLE"));assertFalse(response.body().contains("heldBy"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO ticketing.seats(id,event_id,ticket_type_id,label) VALUES (?,?,?,'A1')",UUID.randomUUID(),event,type));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE ticketing.seats SET status='HELD' WHERE id=?",seats.get(0)));
        var gaBooking=commands.create(owner,"fk-"+event,new GaBookingRequest(event,ga,1),UUID.randomUUID());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE ticketing.seats SET status='HELD',held_by_booking_id=?,hold_expires_at=clock_timestamp()+interval '1m' WHERE id=?",gaBooking.bookingId(),seats.get(0)));
    }
    @Test void seatedHttpSuccessHasLocationPriceItemsAndOwner() throws Exception {
        var r=post("seated-"+event,Map.of("eventId",event,"ticketTypeId",type,"seatIds",seats.subList(0,2)));
        assertEquals(202,r.statusCode(),r.body());var body=json.readTree(r.body());UUID booking=UUID.fromString(body.get("bookingId").asText());
        assertEquals("PENDING_PAYMENT",body.get("status").asText());assertEquals(400000,body.get("totalAmountVnd").asLong());assertEquals("/api/bookings/"+booking,r.headers().firstValue("Location").orElseThrow());
        assertEquals(2,heldSeats());assertEquals(2,count("booking_items"));assertEquals(1,count("outbox_events"));assertEquals(1,count("booking_status_history"));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats s JOIN ticketing.bookings b ON b.id=s.held_by_booking_id WHERE b.id=? AND s.hold_expires_at=b.expires_at",Integer.class,booking));
    }
    @Test void invalidSeatInputsRejectWithoutWrites() throws Exception {
        for(Object ids:List.of(List.of(),List.of(seats.getFirst(),seats.getFirst()),List.of("bad"),List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()))){
            assertEquals(400,post("invalid-"+event,Map.of("eventId",event,"ticketTypeId",type,"seatIds",ids)).statusCode());
        }
        assertEquals(400,post("both-"+event,Map.of("eventId",event,"ticketTypeId",type,"seatIds",seats.subList(0,1),"quantity",1)).statusCode());
        assertEquals(400,post("mode-"+event,Map.of("eventId",event,"ticketTypeId",ga,"seatIds",seats.subList(0,1))).statusCode());
        assertEquals(400,post("unknown-"+event,Map.of("eventId",event,"ticketTypeId",type,"seatIds",List.of(UUID.randomUUID()))).statusCode());
        assertEquals(0,bookings());assertEquals(0,heldSeats());
    }
    @Test void fiftyCompetingCommandsAcrossTwoCustomersHoldOnePair() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var futures=new ArrayList<Future<Integer>>();
            for(int i=0;i<50;i++){int n=i;futures.add(pool.submit(()->{gate.await();try{commands.create(n%2==0?owner:customer,"race-"+event+"-"+n,request(seats.get(n%2),seats.get(1-n%2)),UUID.randomUUID());return 202;}catch(ApiException ex){return ex.status;}}));}
            gate.countDown();int success=0,conflict=0;for(var f:futures){int s=f.get(20,TimeUnit.SECONDS);if(s==202)success++;else if(s==409)conflict++;else fail("Unexpected "+s);}
            assertEquals(1,success);assertEquals(49,conflict);assertEquals(2,heldSeats());assertEquals(1,bookings());assertEquals(1,count("outbox_events"));assertEquals(2,count("booking_items"));
            assertEquals(1,jdbc.queryForObject("SELECT count(DISTINCT held_by_booking_id) FROM ticketing.seats WHERE event_id=? AND status='HELD'",Integer.class,event));
            evidence.add(Map.of("scenario","SEAT-01","commands",50,"customers",2,"success",success,"conflict",conflict,"heldSeats",heldSeats(),"bookings",bookings()));
        }
    }
    @Test void fiftyHttpRequestsForOneSeatHaveOneWinner() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var futures=new ArrayList<Future<Integer>>();
            for(int i=0;i<50;i++){int n=i;futures.add(pool.submit(()->{gate.await();return post("http-"+event+"-"+n,Map.of("eventId",event,"ticketTypeId",type,"seatIds",seats.subList(0,1))).statusCode();}));}
            gate.countDown();int success=0,conflict=0;for(var f:futures){int s=f.get(20,TimeUnit.SECONDS);if(s==202)success++;else if(s==409)conflict++;else fail("Unexpected "+s);}
            assertEquals(1,success);assertEquals(49,conflict);assertEquals(1,heldSeats());assertEquals(1,bookings());
            evidence.add(Map.of("scenario","SEAT-01-HTTP","requests",50,"http202",success,"http409",conflict,"heldSeats",heldSeats(),"bookings",bookings()));
        }
    }
    @Test void partiallyOverlappingSelectionsRaceWithoutLeavingTheLoserSeatHeld() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);
            var futures=new ArrayList<Future<Integer>>();
            for(int side=0;side<2;side++){int n=side;futures.add(pool.submit(()->{
                gate.await();
                try{commands.create(n==0?owner:customer,"overlap-"+event+"-"+n,request(seats.get(n),seats.get(n+1)),UUID.randomUUID());return 202;}
                catch(ApiException ex){assertEquals("INVENTORY_UNAVAILABLE",ex.code);return ex.status;}
            }));}
            gate.countDown();var codes=new ArrayList<Integer>();for(var f:futures)codes.add(f.get(10,TimeUnit.SECONDS));Collections.sort(codes);
            assertEquals(List.of(202,409),codes);assertEquals(2,heldSeats());assertEquals(1,bookings());assertEquals(2,count("booking_items"));assertEquals(1,count("outbox_events"));assertEquals(1,count("idempotency_keys"));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE event_id=? AND status='AVAILABLE'",Integer.class,event));
            assertEquals(1,jdbc.queryForObject("SELECT count(DISTINCT held_by_booking_id) FROM ticketing.seats WHERE event_id=? AND status='HELD'",Integer.class,event));
            evidence.add(Map.of("scenario","SEAT-partial-overlap","customers",2,"success",1,"conflict",1,"heldSeats",2,"bookings",1,"outbox",1));
        }
    }
    @Test void twentySameKeyReversedSeatListsReplayOneBooking() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var futures=new ArrayList<Future<BookingCommandService.Result>>();
            for(int i=0;i<20;i++){int n=i;futures.add(pool.submit(()->{gate.await();return book("duplicate-"+event,seats.get(n%2),seats.get(1-n%2));}));}
            gate.countDown();var ids=new HashSet<UUID>();var bodies=new HashSet<String>();for(var f:futures){var result=f.get(20,TimeUnit.SECONDS);ids.add(result.bookingId());bodies.add(result.body());}
            assertEquals(1,ids.size());assertEquals(1,bodies.size());assertEquals(2,heldSeats());assertEquals(1,count("outbox_events"));
            assertCode(409,"IDEMPOTENCY_KEY_REUSED",()->book("duplicate-"+event,seats.get(2)));
            evidence.add(Map.of("scenario","SEAT-IDEM","commands",20,"distinctIds",ids.size(),"distinctBodies",bodies.size(),"heldSeats",heldSeats(),"outbox",count("outbox_events")));
        }
    }
    @Test void laterUnavailableSeatRollsBackEarlierSeatAndKey(){
        book("occupied-"+event,seats.get(1));
        assertCode(409,"INVENTORY_UNAVAILABLE",()->book("partial-"+event,seats.get(0),seats.get(1)));
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM ticketing.seats WHERE id=?",String.class,seats.get(0)));
        assertEquals(1,bookings());assertEquals(1,heldSeats());assertEquals(1,count("idempotency_keys"));assertEquals(1,count("booking_items"));
        book("partial-"+event,seats.get(0),seats.get(2));assertEquals(3,heldSeats());
    }
    @Test void overdueSeatIsNotTakenInlineAndReplayAfterRebookCannotStealIt(){
        String key="expired-"+event;var first=book(key,seats.get(0));due(first.bookingId());
        assertCode(409,"HOLD_EXPIRY_PROCESSING",()->commands.create(customer,"new-"+event,request(seats.get(0)),UUID.randomUUID()));
        assertEquals(ExpiryService.Outcome.EXPIRED,expiry.expire(first.bookingId()));assertEquals(0,heldSeats());
        var next=commands.create(customer,"new-"+event,request(seats.get(0)),UUID.randomUUID());
        assertEquals(first,book(key,seats.get(0)));assertEquals("EXPIRED",status(first.bookingId()));
        assertEquals(ExpiryService.Outcome.NOT_DUE,expiry.expire(first.bookingId()));
        assertEquals(next.bookingId(),jdbc.queryForObject("SELECT held_by_booking_id FROM ticketing.seats WHERE id=?",UUID.class,seats.get(0)));
    }
    @Test void concurrentWorkersExpireSeatsExactlyOnce() throws Exception {
        var result=book("expiry-"+event,seats.get(0),seats.get(1));due(result.bookingId());
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var futures=new ArrayList<Future<ExpiryService.Outcome>>();
            for(int i=0;i<20;i++)futures.add(pool.submit(()->{gate.await();return expiry.expire(result.bookingId());}));gate.countDown();
            int wins=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS)==ExpiryService.Outcome.EXPIRED)wins++;
            assertEquals(1,wins);assertEquals(0,heldSeats());assertEquals("EXPIRED",status(result.bookingId()));assertEquals(2,count("outbox_events"));assertEquals(2,count("booking_status_history"));
            assertEquals(ExpiryService.Outcome.NOT_DUE,expiry.expire(result.bookingId()));
            evidence.add(Map.of("scenario","EXP-01","workers",20,"expiredTransitions",wins,"heldSeats",heldSeats(),"outbox",count("outbox_events"),"history",count("booking_status_history")));
        }
    }
    @Test void gaExpiryReleasesOnceAndAllowsNewBooking(){
        var result=commands.create(owner,"ga-"+event,new GaBookingRequest(event,ga,3),UUID.randomUUID());due(result.bookingId());
        assertEquals(ExpiryService.Outcome.EXPIRED,expiry.expire(result.bookingId()));assertEquals(ExpiryService.Outcome.NOT_DUE,expiry.expire(result.bookingId()));
        assertEquals(0,jdbc.queryForObject("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,ga));
        commands.create(customer,"ga-next-"+event,new GaBookingRequest(event,ga,4),UUID.randomUUID());
        assertEquals(4,jdbc.queryForObject("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,ga));
    }
    @Test void durableTimelyPaymentConfirmsInsteadOfReleasing(){
        var result=book("payment-"+event,seats.get(0));due(result.bookingId());
        jdbc.update("INSERT INTO ticketing.payments(id,booking_id,request_id,scenario,status,recorded_at) SELECT ?,id,?,'SUCCESS','SUCCESS',expires_at-interval '1ms' FROM ticketing.bookings WHERE id=?",UUID.randomUUID(),UUID.randomUUID(),result.bookingId());
        assertEquals(ExpiryService.Outcome.PROCESSED_PAYMENT,expiry.expire(result.bookingId()));
        assertEquals("CONFIRMED",status(result.bookingId()));assertEquals(0,heldSeats());assertEquals(2,count("outbox_events"));assertEquals(1,count("tickets"));
        assertEquals("SOLD",jdbc.queryForObject("SELECT status FROM ticketing.seats WHERE id=?",String.class,seats.get(0)));
    }
    @Test void scheduledPaymentHasNoDurableResultAndDoesNotBlockExpiry(){
        var result=book("scheduled-"+event,seats.get(0));due(result.bookingId());
        jdbc.update("INSERT INTO ticketing.payments(id,booking_id,request_id,scenario,status,result_due_at) VALUES (?,?,?,'DELAY_TIMEOUT','SCHEDULED',clock_timestamp()+interval '1h')",UUID.randomUUID(),result.bookingId(),UUID.randomUUID());
        assertEquals(ExpiryService.Outcome.EXPIRED,expiry.expire(result.bookingId()));assertEquals(0,heldSeats());
    }
    @Test void notDueTerminalAndLockedBookingsAreNotReleased() throws Exception {
        var result=book("notdue-"+event,seats.get(0));assertEquals(ExpiryService.Outcome.NOT_DUE,expiry.expire(result.bookingId()));due(result.bookingId());
        try(var c=connection()){c.setAutoCommit(false);try(var s=c.createStatement()){s.executeQuery("SELECT id FROM ticketing.bookings WHERE id='"+result.bookingId()+"' FOR UPDATE");}
            assertEquals(ExpiryService.Outcome.BUSY,expiry.expire(result.bookingId()));assertEquals(1,heldSeats());c.rollback();}
        assertEquals(ExpiryService.Outcome.EXPIRED,expiry.expire(result.bookingId()));assertEquals(ExpiryService.Outcome.NOT_DUE,expiry.expire(result.bookingId()));
    }
    @Test void timeoutOnSecondSeatLeavesNoPartialHoldAndCanRetry() throws Exception {
        try(var c=connection()){c.setAutoCommit(false);try(var s=c.createStatement()){s.executeQuery("SELECT id FROM ticketing.seats WHERE id='"+seats.get(1)+"' FOR UPDATE");}
            var r=post("timeout-"+event,Map.of("eventId",event,"ticketTypeId",type,"seatIds",seats.subList(0,2)));
            assertEquals(503,r.statusCode(),r.body());assertEquals("1",r.headers().firstValue("Retry-After").orElseThrow());assertTrue(r.body().contains("BOOKING_BUSY"));
            assertEquals(0,heldSeats());assertEquals(0,bookings());assertEquals(0,count("outbox_events"));c.rollback();}
        book("timeout-"+event,seats.get(0),seats.get(1));assertEquals(2,heldSeats());
    }
    @Test void expiryFailureAfterOutboxRollsBackAndNextPassContinues(){
        var result=book("expiry-failure-"+event,seats.get(0),seats.get(1));due(result.bookingId());
        jdbc.execute("CREATE FUNCTION ticketing.it_fail_expiry() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"+result.bookingId()+"' AND NEW.to_status='EXPIRED' THEN RAISE EXCEPTION 'expiry rollback fixture'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_fail_expiry BEFORE INSERT ON ticketing.booking_status_history FOR EACH ROW EXECUTE FUNCTION ticketing.it_fail_expiry()");
        try{assertCode(500,"INTERNAL_ERROR",()->expiry.expire(result.bookingId()));assertEquals("PENDING_PAYMENT",status(result.bookingId()));assertEquals(2,heldSeats());assertEquals(1,count("outbox_events"));assertEquals(1,count("booking_status_history"));}
        finally{jdbc.execute("DROP TRIGGER it_fail_expiry ON ticketing.booking_status_history");jdbc.execute("DROP FUNCTION ticketing.it_fail_expiry()");}
        assertEquals(ExpiryService.Outcome.EXPIRED,expiry.expire(result.bookingId()));
    }
    @Test void seatBookingFailureAfterOutboxRollsBackAll(){
        jdbc.execute("CREATE FUNCTION ticketing.it_fail_seat_booking() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.key='seat-failure-"+event+"' THEN RAISE EXCEPTION 'seat rollback fixture'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_fail_seat_booking BEFORE UPDATE ON ticketing.idempotency_keys FOR EACH ROW EXECUTE FUNCTION ticketing.it_fail_seat_booking()");
        try{assertCode(500,"INTERNAL_ERROR",()->book("seat-failure-"+event,seats.get(0),seats.get(1)));assertEquals(0,heldSeats());assertEquals(0,bookings());assertEquals(0,count("outbox_events"));assertEquals(0,count("booking_items"));}
        finally{jdbc.execute("DROP TRIGGER it_fail_seat_booking ON ticketing.idempotency_keys");jdbc.execute("DROP FUNCTION ticketing.it_fail_seat_booking()");}
        book("seat-failure-"+event,seats.get(0),seats.get(1));assertEquals(2,heldSeats());
    }
    @Test void expiryLockTimeoutRollsBackThenRetriesSafely() throws Exception {
        var result=book("expiry-lock-"+event,seats.get(0),seats.get(1));due(result.bookingId());
        try(var c=connection()){c.setAutoCommit(false);try(var s=c.createStatement()){s.executeQuery("SELECT id FROM ticketing.seats WHERE id='"+seats.get(1)+"' FOR UPDATE");}
            assertCode(503,"BOOKING_BUSY",()->expiry.expire(result.bookingId()));assertEquals(2,heldSeats());assertEquals("PENDING_PAYMENT",status(result.bookingId()));assertEquals(1,count("outbox_events"));c.rollback();}
        assertEquals(ExpiryService.Outcome.EXPIRED,expiry.expire(result.bookingId()));assertEquals(0,heldSeats());
    }
    @Test void newBookingWaitsForWorkerCommitAndOldWorkerCannotReleaseNewOwner() throws Exception {
        var first=book("old-"+event,seats.get(0));due(first.bookingId());int key=100000+ThreadLocalRandom.current().nextInt(1000000);
        jdbc.execute("CREATE FUNCTION ticketing.it_expiry_gate() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"+first.bookingId()+"' AND NEW.to_status='EXPIRED' THEN PERFORM pg_advisory_xact_lock("+key+"); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_expiry_gate BEFORE INSERT ON ticketing.booking_status_history FOR EACH ROW EXECUTE FUNCTION ticketing.it_expiry_gate()");
        try(var c=connection();var pool=Executors.newVirtualThreadPerTaskExecutor()){
            try(var s=c.createStatement()){s.executeQuery("SELECT pg_advisory_lock("+key+")");}
            var worker=pool.submit(()->expiry.expire(first.bookingId()));boolean waiting=false;
            for(int i=0;i<100;i++){if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND objid=? AND NOT granted)",Boolean.class,key)){waiting=true;break;}Thread.sleep(5);}
            assertTrue(waiting,"Worker must reach the gate after release, before commit");
            var next=pool.submit(()->commands.create(customer,"new-"+event,request(seats.get(0)),UUID.randomUUID()));
            Thread.sleep(80);assertFalse(next.isDone());assertEquals(1,heldSeats());assertEquals("PENDING_PAYMENT",status(first.bookingId()));
            try(var s=c.createStatement()){s.executeQuery("SELECT pg_advisory_unlock("+key+")");}
            assertEquals(ExpiryService.Outcome.EXPIRED,worker.get(10,TimeUnit.SECONDS));var newer=next.get(10,TimeUnit.SECONDS);
            assertEquals(ExpiryService.Outcome.NOT_DUE,expiry.expire(first.bookingId()));assertEquals(newer.bookingId(),jdbc.queryForObject("SELECT held_by_booking_id FROM ticketing.seats WHERE id=?",UUID.class,seats.get(0)));
        }finally{jdbc.execute("DROP TRIGGER it_expiry_gate ON ticketing.booking_status_history");jdbc.execute("DROP FUNCTION ticketing.it_expiry_gate()");}
    }
    @Test void realPostgresDeadlockIsRetriedWithinThreeAttempts() throws Exception {
        var gate=new CyclicBarrier(2);var deadlocks=new AtomicInteger();var attempts=new AtomicInteger();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<Integer>>();
            for(int side=0;side<2;side++){int n=side;var local=new AtomicInteger();futures.add(pool.submit(()->transactions.run(()->{
                int attempt=local.incrementAndGet();attempts.incrementAndGet();
                try{
                    jdbc.update("UPDATE ticketing.seats SET version=version+1 WHERE id=?",seats.get(n));
                    if(attempt==1)try{gate.await(3,TimeUnit.SECONDS);}catch(Exception ex){throw new IllegalStateException(ex);}
                    jdbc.update("UPDATE ticketing.seats SET version=version+1 WHERE id=?",seats.get(1-n));return 1;
                }catch(RuntimeException ex){if("40P01".equals(BookingTransactions.sqlState(ex)))deadlocks.incrementAndGet();throw ex;}
            })));}
            for(var f:futures)assertEquals(1,f.get(15,TimeUnit.SECONDS));
            assertEquals(1,deadlocks.get());assertEquals(3,attempts.get());
            for(int i=0;i<2;i++)assertEquals(2,jdbc.queryForObject("SELECT version FROM ticketing.seats WHERE id=?",Integer.class,seats.get(i)));
            assertEquals(0,heldSeats());assertEquals(0,bookings());
            evidence.add(Map.of("scenario","real-40P01","transactions",2,"deadlocks",deadlocks.get(),"attempts",attempts.get(),"committed",2));
        }
    }
    @Test void batchIsLimitedToFiftyAndRepeatedPassFinishesTheRest(){
        jdbc.update("UPDATE ticketing.inventory_ga SET capacity=60 WHERE ticket_type_id=?",ga);
        for(int i=0;i<51;i++)commands.create(owner,"batch-"+event+"-"+i,new GaBookingRequest(event,ga,1),UUID.randomUUID());
        jdbc.update("UPDATE ticketing.bookings SET created_at=clock_timestamp()-interval '20m',expires_at=clock_timestamp()-interval '1s' WHERE event_id=?",event);
        var first=expiry.runOnce();assertEquals(50,first.candidates());assertEquals(50,first.expired());assertEquals(0,first.failed());
        assertEquals(1,jdbc.queryForObject("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,ga));
        assertEquals(1,expiry.runOnce().expired());assertEquals(0,expiry.runOnce().expired());
        assertEquals(0,jdbc.queryForObject("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,ga));
    }
    @Test void oneFailedBookingDoesNotRollBackAnotherBookingInThePass(){
        var failing=book("batch-failure-"+event,seats.get(0));var healthy=book("batch-good-"+event,seats.get(1));due(failing.bookingId());due(healthy.bookingId());
        jdbc.execute("CREATE FUNCTION ticketing.it_batch_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"+failing.bookingId()+"' AND NEW.to_status='EXPIRED' THEN RAISE EXCEPTION 'batch failure fixture'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_batch_failure BEFORE INSERT ON ticketing.booking_status_history FOR EACH ROW EXECUTE FUNCTION ticketing.it_batch_failure()");
        try{var pass=expiry.runOnce();assertEquals(1,pass.failed());assertEquals(1,pass.expired());assertEquals("PENDING_PAYMENT",status(failing.bookingId()));assertEquals("EXPIRED",status(healthy.bookingId()));assertEquals(1,heldSeats());}
        finally{jdbc.execute("DROP TRIGGER it_batch_failure ON ticketing.booking_status_history");jdbc.execute("DROP FUNCTION ticketing.it_batch_failure()");}
        assertEquals(1,expiry.runOnce().expired());assertEquals(0,heldSeats());
    }
    @Test void repeatedDeadlockSqlstateStopsAfterThreeAttemptsAndRollsBackSeats(){
        String key="deadlock-limit-"+event;
        jdbc.execute("CREATE SEQUENCE ticketing.it_deadlock_attempts");
        jdbc.execute("CREATE FUNCTION ticketing.it_deadlock_limit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.key='"+key+"' THEN PERFORM nextval('ticketing.it_deadlock_attempts'); RAISE EXCEPTION 'deadlock limit fixture' USING ERRCODE='40P01'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_deadlock_limit BEFORE UPDATE ON ticketing.idempotency_keys FOR EACH ROW EXECUTE FUNCTION ticketing.it_deadlock_limit()");
        try{assertCode(503,"BOOKING_BUSY",()->book(key,seats.get(0),seats.get(1)));assertEquals(3,jdbc.queryForObject("SELECT last_value FROM ticketing.it_deadlock_attempts",Integer.class));assertEquals(0,heldSeats());assertEquals(0,bookings());assertEquals(0,count("outbox_events"));}
        finally{jdbc.execute("DROP TRIGGER it_deadlock_limit ON ticketing.idempotency_keys");jdbc.execute("DROP FUNCTION ticketing.it_deadlock_limit()");jdbc.execute("DROP SEQUENCE ticketing.it_deadlock_attempts");}
    }
    @AfterAll static void writeEvidence() throws Exception {
        Files.writeString(Path.of("target/part5-evidence.json"),new ObjectMapper().writeValueAsString(evidence));
    }
}
