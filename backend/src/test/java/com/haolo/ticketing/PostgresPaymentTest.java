package com.haolo.ticketing;

import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.payments.*;
import com.haolo.ticketing.expiry.ExpiryService;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.tickets.TicketService;
import com.haolo.ticketing.shared.ApiException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=${PG_IT_URL}","expiry.enabled=false","messaging.enabled=false"})
@ActiveProfiles("test") @EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+")
class PostgresPaymentTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingCommandService bookings;
    @Autowired BookingCancelService cancel;
    @Autowired PaymentSimulatorService simulator;
    @Autowired PaymentResultProcessor processor;
    @Autowired PaymentMaintenance maintenance;
    @Autowired ExpiryService expiry;
    @Autowired TicketService tickets;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    UUID event,ga,seated,foreign;
    List<UUID> seats;
    final DemoPrincipal owner=new DemoPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"),"CUSTOMER");
    DemoPrincipal other;
    final HttpClient http=HttpClient.newHttpClient();
    static final List<Map<String,Object>> evidence=new ArrayList<>();
    void snap(String scenario,String phase){RecoveryEvidence.capture(jdbc,scenario,phase,event);}
    @BeforeEach void fixture(){
        event=UUID.randomUUID();ga=UUID.randomUUID();seated=UUID.randomUUID();foreign=UUID.randomUUID();other=new DemoPrincipal(foreign,"CUSTOMER");
        jdbc.update("INSERT INTO ticketing.users(id,demo_role,display_name) VALUES (?,'CUSTOMER','Payment test')",foreign);
        jdbc.update("INSERT INTO ticketing.events(id,title,description,venue,sale_start_at,sale_end_at,starts_at,status) VALUES (?,'Payment test','','Local',clock_timestamp()-interval '1h',clock_timestamp()+interval '1h',clock_timestamp()+interval '2h','PUBLISHED')",event);
        jdbc.update("INSERT INTO ticketing.ticket_types(id,event_id,name,mode,price_vnd) VALUES (?,?,'GA','GA',100000),(?,?,'Seats','SEATED',200000)",ga,event,seated,event);
        jdbc.update("INSERT INTO ticketing.inventory_ga(ticket_type_id,capacity) VALUES (?,10)",ga);
        seats=new ArrayList<>();for(int i=0;i<3;i++)seats.add(UUID.randomUUID());seats.sort(Comparator.comparing(UUID::toString));
        for(int i=0;i<3;i++)jdbc.update("INSERT INTO ticketing.seats(id,event_id,ticket_type_id,label) VALUES (?,?,?,?)",seats.get(i),event,seated,"A"+(i+1));
    }
    @AfterEach void cleanup(){
        for(String table:List.of("tickets","payment_compensations","payments","idempotency_keys","booking_status_history","outbox_events")){
            String col=table.equals("outbox_events")?"aggregate_id":"booking_id";
            jdbc.update("DELETE FROM ticketing."+table+" WHERE "+col+" IN(SELECT id FROM ticketing.bookings WHERE event_id=?)",event);
        }
        jdbc.update("DELETE FROM ticketing.booking_items WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.seats WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.bookings WHERE event_id=?",event);
        jdbc.update("DELETE FROM ticketing.inventory_ga WHERE ticket_type_id=?",ga);jdbc.update("DELETE FROM ticketing.ticket_types WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.events WHERE id=?",event);jdbc.update("DELETE FROM ticketing.users WHERE id=?",foreign);
    }
    UUID ga(int quantity){return bookings.create(owner,"ga-"+UUID.randomUUID(),new GaBookingRequest(event,ga,quantity),UUID.randomUUID()).bookingId();}
    UUID seat(int quantity){return bookings.create(owner,"seat-"+UUID.randomUUID(),new SeatBookingRequest(event,seated,seats.subList(0,quantity)),UUID.randomUUID()).bookingId();}
    String state(UUID id){return jdbc.queryForObject("SELECT status FROM ticketing.bookings WHERE id=?",String.class,id);}
    int rows(String table,UUID id){return jdbc.queryForObject("SELECT count(*) FROM ticketing."+table+" WHERE "+(table.equals("outbox_events")?"aggregate_id":"booking_id")+"=?",Integer.class,id);}
    int count(String type,UUID id){return jdbc.queryForObject("SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id=? AND type=?",Integer.class,id,type);}
    int held(){return jdbc.queryForObject("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,ga);}
    int sold(){return jdbc.queryForObject("SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,ga);}
    SimulationInput input(String scenario){return new SimulationInput(scenario,UUID.randomUUID());}
    PaymentSimulatorService.Receipt record(UUID id,String scenario){return simulator.record(owner,id,input(scenario),UUID.randomUUID());}
    void pay(UUID id,String scenario){simulator.simulate(owner,id,input(scenario),UUID.randomUUID());}
    void due(UUID id){jdbc.update("UPDATE ticketing.bookings SET created_at=clock_timestamp()-interval '20m',expires_at=clock_timestamp()-interval '1s' WHERE id=?",id);jdbc.update("UPDATE ticketing.seats SET hold_expires_at=(SELECT expires_at FROM ticketing.bookings WHERE id=?) WHERE held_by_booking_id=?",id,id);}
    void code(int status,String code,Runnable action){var ex=assertThrows(ApiException.class,action::run);assertEquals(status,ex.status);assertEquals(code,ex.code);}
    HttpResponse<String> send(String method,String path,String body,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return http.send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> simulate(UUID id,SimulationInput input) throws Exception{return send("POST","/api/bookings/"+id+"/simulate-payment",json.writeValueAsString(input),"demo-customer-token");}
    Connection connection() throws Exception{return DriverManager.getConnection(System.getenv("PG_IT_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"));}
    @Test void gaSuccessIssuesExactlyTheQuantityAndMovesHeldToSold() throws Exception {
        UUID id=ga(3);var response=simulate(id,input("SUCCESS"));assertEquals(202,response.statusCode(),response.body());assertEquals("ACCEPTED",json.readTree(response.body()).get("status").asText());
        assertEquals("CONFIRMED",state(id));assertEquals(0,held());assertEquals(3,sold());var result=tickets.list(owner,id);assertEquals(3,result.size());
        assertEquals(Set.of(1,2,3),new HashSet<>(result.stream().map(TicketService.Ticket::ticketOrdinal).toList()));assertEquals(3,new HashSet<>(result.stream().map(TicketService.Ticket::code).toList()).size());assertTrue(result.stream().allMatch(t->t.seatId()==null&&t.seatLabel()==null));
        assertEquals(1,count("PaymentResultRecorded",id));assertEquals(1,count("BookingConfirmed",id));assertEquals(2,rows("booking_status_history",id));
    }
    @Test void seatedSuccessSellsOnlyOwnedSeatsAndTicketReferencesAreCorrect(){
        UUID id=seat(2);pay(id,"SUCCESS");assertEquals("CONFIRMED",state(id));var result=tickets.list(owner,id);assertEquals(2,result.size());
        assertEquals(new HashSet<>(seats.subList(0,2)),new HashSet<>(result.stream().map(TicketService.Ticket::seatId).toList()));
        assertTrue(result.stream().allMatch(t->t.ticketOrdinal()==1&&t.seatLabel().startsWith("A")));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE held_by_booking_id=? AND status='SOLD' AND hold_expires_at IS NULL",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE event_id=? AND status='AVAILABLE'",Integer.class,event));
    }
    @Test void duplicateHttpRequestsAndCallbacksDoNotIssueMoreTickets() throws Exception {
        UUID id=ga(3);var input=input("SUCCESS");
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var futures=new ArrayList<Future<HttpResponse<String>>>();for(int i=0;i<20;i++)futures.add(pool.submit(()->{gate.await();return simulate(id,input);}));gate.countDown();
            var bodies=new HashSet<String>();for(var f:futures){var r=f.get(20,TimeUnit.SECONDS);assertEquals(202,r.statusCode(),r.body());bodies.add(r.body());}assertEquals(1,bodies.size());
        }
        UUID payment=jdbc.queryForObject("SELECT id FROM ticketing.payments WHERE booking_id=?",UUID.class,id);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<Integer>>();for(int i=0;i<20;i++)futures.add(pool.submit(()->processor.process(id,payment)));for(var f:futures)assertEquals(0,f.get(15,TimeUnit.SECONDS));
        }
        assertEquals(1,rows("payments",id));assertEquals(3,rows("tickets",id));assertEquals(1,count("BookingConfirmed",id));assertEquals(1,count("PaymentResultRecorded",id));assertEquals(3,sold());assertEquals(0,held());
        evidence.add(Map.of("scenario","duplicate-payment","httpRequests",20,"duplicateCallbacks",20,"payments",1,"tickets",3,"confirmedEvents",1,"sold",sold(),"held",held()));
    }
    @Test void changedScenarioWithTheSameRequestIdConflicts() throws Exception {
        UUID id=ga(1);var in=input("SUCCESS");assertEquals(202,simulate(id,in).statusCode());var changed=simulate(id,new SimulationInput("FAILURE",in.requestId()));
        assertEquals(409,changed.statusCode());assertTrue(changed.body().contains("IDEMPOTENCY_KEY_REUSED"));assertEquals(1,rows("payments",id));assertEquals("CONFIRMED",state(id));
    }
    @Test void httpAuthValidationOwnerAndTicketAccess() throws Exception {
        UUID id=ga(1);String path="/api/bookings/"+id+"/simulate-payment",body=json.writeValueAsString(input("SUCCESS"));
        assertEquals(401,send("POST",path,body,null).statusCode());assertEquals(401,send("POST",path,body,"unknown").statusCode());assertEquals(403,send("POST",path,body,"demo-admin-token").statusCode());
        for(String invalid:List.of("{}","{\"scenario\":\"SUCCESS\",\"requestId\":\"bad\"}","{\"scenario\":\"unknown\",\"requestId\":\""+UUID.randomUUID()+"\"}",body.substring(0,body.length()-1)+",\"recordedAt\":\"2020-01-01T00:00:00Z\"}",body.substring(0,body.length()-1)+",\"status\":\"SUCCESS\"}"))assertEquals(400,send("POST",path,invalid,"demo-customer-token").statusCode(),invalid);
        var foreignBooking=bookings.create(other,"foreign",new GaBookingRequest(event,ga,1),UUID.randomUUID()).bookingId();
        assertEquals(404,simulate(foreignBooking,input("SUCCESS")).statusCode());assertEquals(404,send("GET","/api/bookings/"+foreignBooking+"/tickets","","demo-customer-token").statusCode());
        assertEquals(200,send("GET","/api/bookings/"+foreignBooking+"/tickets","","demo-admin-token").statusCode());assertEquals("[]",send("GET","/api/bookings/"+id+"/tickets","","demo-customer-token").body());assertEquals(0,rows("payments",id));
    }
    @Test void invalidInternalCallbacksCannotProcessForeignUnknownOrScheduledResults(){
        UUID id=ga(1),another=ga(1);var result=record(id,"SUCCESS");
        code(400,"VALIDATION_ERROR",()->processor.process(another,result.paymentId()));code(404,"NOT_FOUND",()->processor.process(id,UUID.randomUUID()));
        var pending=record(another,"DELAY_TIMEOUT");code(400,"VALIDATION_ERROR",()->processor.process(another,pending.paymentId()));assertEquals("PENDING_PAYMENT",state(id));assertEquals("PENDING_PAYMENT",state(another));assertEquals(0,rows("tickets",id));
    }
    @Test void failureReleasesGaOnceAndRepeatedResultsDoNotSubtractAgain(){
        UUID id=ga(3);snap("ga-payment-failure","before");var input=input("FAILURE");simulator.simulate(owner,id,input,UUID.randomUUID());snap("ga-payment-failure","released");simulator.simulate(owner,id,input,UUID.randomUUID());pay(id,"FAILURE");
        assertEquals("PAYMENT_FAILED",state(id));assertEquals(0,held());assertEquals(0,sold());assertEquals(0,rows("tickets",id));assertEquals(1,count("BookingPaymentFailed",id));
        snap("ga-payment-failure","repeated");
    }
    @Test void failureReleasesAllSeatsOnce(){
        UUID id=seat(2);snap("seat-payment-failure","before");pay(id,"FAILURE");snap("seat-payment-failure","released");pay(id,"FAILURE");assertEquals("PAYMENT_FAILED",state(id));assertEquals(0,rows("tickets",id));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE event_id=? AND status='AVAILABLE' AND held_by_booking_id IS NULL",Integer.class,event));assertEquals(1,count("BookingPaymentFailed",id));
        snap("seat-payment-failure","repeated");
    }
    @Test void durableTimelyResultWithProcessingLagIsConfirmedByExpiry(){
        UUID id=ga(2);var result=record(id,"SUCCESS");due(id);
        jdbc.update("UPDATE ticketing.payments SET recorded_at=(SELECT expires_at-interval '1ms' FROM ticketing.bookings WHERE id=?) WHERE id=?",id,result.paymentId());
        assertEquals(ExpiryService.Outcome.PROCESSED_PAYMENT,expiry.expire(id));assertEquals("CONFIRMED",state(id));assertEquals(2,sold());assertEquals(0,held());assertEquals(2,rows("tickets",id));assertEquals(0,count("BookingExpired",id));
        assertEquals(0,processor.process(id,result.paymentId()));assertEquals(3,jdbc.queryForObject("SELECT aggregate_version FROM ticketing.bookings WHERE id=?",Integer.class,id));
        evidence.add(Map.of("scenario","PAY-01-lag","recordedBeforeExpiry",true,"processedByExpiry",true,"status",state(id),"tickets",rows("tickets",id),"expiredEvents",count("BookingExpired",id)));
    }
    @Test void exactDeadlineIsTimelyButOneMicrosecondLateIsNot(){
        UUID exact=ga(1);var a=record(exact,"SUCCESS");jdbc.update("UPDATE ticketing.bookings SET created_at=(SELECT recorded_at-interval '1m' FROM ticketing.payments WHERE id=?),expires_at=(SELECT recorded_at FROM ticketing.payments WHERE id=?) WHERE id=?",a.paymentId(),a.paymentId(),exact);processor.process(exact,a.paymentId());assertEquals("CONFIRMED",state(exact));
        UUID late=ga(1);var b=record(late,"SUCCESS");jdbc.update("UPDATE ticketing.bookings SET created_at=(SELECT recorded_at-interval '1m' FROM ticketing.payments WHERE id=?),expires_at=(SELECT recorded_at-interval '1microsecond' FROM ticketing.payments WHERE id=?) WHERE id=?",b.paymentId(),b.paymentId(),late);processor.process(late,b.paymentId());assertEquals("REFUND_REQUIRED",state(late));assertEquals(0,rows("tickets",late));assertEquals(1,rows("payment_compensations",late));
    }
    @Test void lateSuccessBeforeExpiryWorkerCommitsBothExpiryAndRefundHistory(){
        UUID id=ga(2);due(id);var result=record(id,"SUCCESS");assertEquals(ExpiryService.Outcome.PROCESSED_PAYMENT,expiry.expire(id));assertEquals("REFUND_REQUIRED",state(id));
        assertEquals(0,held());assertEquals(0,sold());assertEquals(0,rows("tickets",id));assertEquals(1,count("BookingExpired",id));assertEquals(1,count("BookingRefundRequired",id));assertEquals(3,rows("booking_status_history",id));assertEquals(1,rows("payment_compensations",id));
        processor.process(id,result.paymentId());assertEquals(1,rows("payment_compensations",id));
    }
    @Test void expiredSeatBookingNeverTakesInventoryFromTheNewOwner(){
        UUID old=seat(2);due(old);assertEquals(ExpiryService.Outcome.EXPIRED,expiry.expire(old));
        snap("late-success-seat-reallocated","expired");
        UUID next=bookings.create(other,"new-owner",new SeatBookingRequest(event,seated,seats.subList(0,2)),UUID.randomUUID()).bookingId();snap("late-success-seat-reallocated","new-owner-held");pay(old,"SUCCESS");
        assertEquals("REFUND_REQUIRED",state(old));assertEquals("PENDING_PAYMENT",state(next));assertEquals(0,rows("tickets",old));assertEquals(1,rows("payment_compensations",old));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE held_by_booking_id=? AND status='HELD'",Integer.class,next));
        snap("late-success-seat-reallocated","late-result-processed");
        evidence.add(Map.of("scenario","PAY-02-reallocated-seats","oldStatus",state(old),"oldTickets",0,"compensations",1,"newOwnerHeldSeats",2));
    }
    @Test void delayedLedgerIsDurableAndMaintenanceRecordsAndProcessesOnce(){
        UUID id=ga(2);var result=record(id,"DELAY_TIMEOUT");assertTrue(result.scheduled());assertEquals(0,count("PaymentResultRecorded",id));assertFalse(simulator.recordDue(id,result.paymentId()));
        snap("scheduled-payment","scheduled");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticketing.payments p JOIN ticketing.bookings b ON b.id=p.booking_id WHERE p.id=? AND p.recorded_at IS NULL AND p.result_due_at=b.expires_at+interval '1s'",Integer.class,result.paymentId()));
        due(id);expiry.expire(id);jdbc.update("UPDATE ticketing.payments SET result_due_at=clock_timestamp()-interval '1ms' WHERE id=?",result.paymentId());
        assertEquals(1,maintenance.runOnce().processed());assertEquals(0,maintenance.runOnce().processed());assertEquals("REFUND_REQUIRED",state(id));assertEquals(1,count("PaymentResultRecorded",id));assertEquals(1,rows("payment_compensations",id));assertEquals(0,rows("tickets",id));
        snap("scheduled-payment","processed-once");
    }
    @Test void successAfterCancelledOrFailedCreatesObligationWithoutChangingTerminalState(){
        UUID cancelled=ga(1);cancel.cancel(owner,cancelled,UUID.randomUUID());pay(cancelled,"SUCCESS");assertEquals("CANCELLED",state(cancelled));assertEquals(1,rows("payment_compensations",cancelled));assertEquals(0,count("BookingRefundRequired",cancelled));
        UUID failed=ga(1);pay(failed,"FAILURE");pay(failed,"SUCCESS");assertEquals("PAYMENT_FAILED",state(failed));assertEquals(1,rows("payment_compensations",failed));assertEquals(0,rows("tickets",failed));assertEquals(0,held());
    }
    @Test void failureAfterExpiredOrConfirmedDoesNotReleaseInventoryAgain(){
        UUID confirmed=ga(2);pay(confirmed,"SUCCESS");pay(confirmed,"FAILURE");assertEquals("CONFIRMED",state(confirmed));assertEquals(2,sold());assertEquals(2,rows("tickets",confirmed));
        UUID expired=ga(2);due(expired);expiry.expire(expired);UUID next=ga(3);pay(expired,"FAILURE");assertEquals("EXPIRED",state(expired));assertEquals(3,held());assertEquals(2,sold());assertEquals("PENDING_PAYMENT",state(next));
    }
    @Test void twoLatePaymentsHaveTwoObligationsButEachPaymentOnlyOne(){
        UUID id=ga(1);due(id);expiry.expire(id);SimulationInput a=input("SUCCESS"),b=input("SUCCESS");
        simulator.simulate(owner,id,a,UUID.randomUUID());simulator.simulate(owner,id,a,UUID.randomUUID());simulator.simulate(owner,id,b,UUID.randomUUID());
        assertEquals(2,rows("payments",id));assertEquals(2,rows("payment_compensations",id));assertEquals(1,count("BookingRefundRequired",id));assertEquals("REFUND_REQUIRED",state(id));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.payment_compensations WHERE booking_id=? AND status='PENDING' AND processed_at IS NULL AND attempt_count=0",Integer.class,id));
    }
    @Test void pendingTimelySuccessDominatesAnOlderUnprocessedFailure(){
        UUID id=ga(2);var failure=record(id,"FAILURE");record(id,"SUCCESS");assertEquals(2,processor.process(id,failure.paymentId()));
        assertEquals("CONFIRMED",state(id));assertEquals(2,sold());assertEquals(0,held());assertEquals(0,count("BookingPaymentFailed",id));assertEquals(2,rows("tickets",id));
    }
    @Test void cancelIsBodylessOwnerOnlyAndRepeatedCancellationIsSafe() throws Exception {
        UUID id=seat(2);String path="/api/bookings/"+id+"/cancel";
        assertEquals(400,send("POST",path,"{}","demo-customer-token").statusCode());assertEquals(403,send("POST",path,"","demo-admin-token").statusCode());
        assertEquals(200,send("POST",path,"","demo-customer-token").statusCode());assertEquals(200,send("POST",path,"","demo-customer-token").statusCode());assertEquals("CANCELLED",state(id));assertEquals(1,count("BookingCancelled",id));assertEquals(0,rows("tickets",id));
        UUID confirmed=ga(1);pay(confirmed,"SUCCESS");assertEquals(409,send("POST","/api/bookings/"+confirmed+"/cancel","","demo-customer-token").statusCode());
    }
    @Test void cancelCannotUndoADurableTimelySuccessEvenBeforeAdapterRuns(){
        UUID id=ga(2);record(id,"SUCCESS");code(409,"INVALID_BOOKING_STATE",()->cancel.cancel(owner,id,UUID.randomUUID()));
        assertEquals("CONFIRMED",state(id));assertEquals(2,rows("tickets",id));assertEquals(2,sold());assertEquals(0,count("BookingCancelled",id));
    }
    @Test void cancellationAndSuccessRaceNeverMixSoldAndReleasedEffects() throws Exception {
        int confirmed=0,cancelled=0;
        for(int i=0;i<8;i++){UUID id=ga(1);
            try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
                var gate=new CountDownLatch(1);var payment=pool.submit(()->{gate.await();pay(id,"SUCCESS");return 1;});var cancellation=pool.submit(()->{gate.await();try{cancel.cancel(owner,id,UUID.randomUUID());return 200;}catch(ApiException ex){return ex.status;}});gate.countDown();payment.get(15,TimeUnit.SECONDS);int status=cancellation.get(15,TimeUnit.SECONDS);
                if(state(id).equals("CONFIRMED")){confirmed++;assertEquals(409,status);assertEquals(1,rows("tickets",id));assertEquals(0,rows("payment_compensations",id));}
                else{cancelled++;assertEquals("CANCELLED",state(id));assertEquals(200,status);assertEquals(0,rows("tickets",id));assertEquals(1,rows("payment_compensations",id));}
            }
        }
        assertEquals(8,confirmed+cancelled);assertEquals(confirmed,sold());assertEquals(0,held());evidence.add(Map.of("scenario","cancel-success-race","iterations",8,"confirmed",confirmed,"cancelled",cancelled,"held",held(),"sold",sold()));
    }
    @Test void paymentAndExpiryRacingNearDeadlineUseStoredDatabaseTime() throws Exception {
        int confirmed=0,refund=0;
        for(int i=0;i<8;i++){UUID id=ga(1);jdbc.update("UPDATE ticketing.bookings SET created_at=clock_timestamp()-interval '1m',expires_at=clock_timestamp()+interval '100ms' WHERE id=?",id);
            try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
                var payment=pool.submit(()->{Thread.sleep(80);pay(id,"SUCCESS");return 1;});var worker=pool.submit(()->{Thread.sleep(110);return expiry.expire(id);});payment.get(15,TimeUnit.SECONDS);worker.get(15,TimeUnit.SECONDS);
            }
            boolean timely=jdbc.queryForObject("SELECT recorded_at<=expires_at FROM ticketing.payments p JOIN ticketing.bookings b ON b.id=p.booking_id WHERE b.id=?",Boolean.class,id);
            if(state(id).equals("CONFIRMED")){confirmed++;assertTrue(timely);assertEquals(1,rows("tickets",id));assertEquals(0,count("BookingExpired",id));}
            else{refund++;assertEquals("REFUND_REQUIRED",state(id));assertFalse(timely);assertEquals(0,rows("tickets",id));assertEquals(1,rows("payment_compensations",id));}
        }
        assertEquals(8,confirmed+refund);assertEquals(confirmed,sold());assertEquals(0,held());evidence.add(Map.of("scenario","deadline-race","iterations",8,"confirmed",confirmed,"refundRequired",refund,"held",held(),"sold",sold()));
    }
    @Test void adminCapacityAndPriceRacesPreserveTheSnapshotAndInventory() throws Exception {
        UUID id=ga(3);String bad=json.writeValueAsString(Map.of("ticketTypes",List.of(Map.of("id",ga,"capacity",2,"priceVnd",999999))));
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var payment=pool.submit(()->{gate.await();pay(id,"SUCCESS");return 1;});var admin=pool.submit(()->{gate.await();return send("PATCH","/api/admin/events/"+event,bad,"demo-admin-token");});gate.countDown();payment.get(15,TimeUnit.SECONDS);var response=admin.get(15,TimeUnit.SECONDS);assertEquals(409,response.statusCode(),response.body());
        }
        assertEquals(3,sold());assertEquals(0,held());assertEquals(300000,jdbc.queryForObject("SELECT total_amount_vnd FROM ticketing.bookings WHERE id=?",Integer.class,id));
        String price=json.writeValueAsString(Map.of("ticketTypes",List.of(Map.of("id",ga,"priceVnd",300000,"capacity",4))));assertEquals(200,send("PATCH","/api/admin/events/"+event,price,"demo-admin-token").statusCode());
        assertEquals(100000,jdbc.queryForObject("SELECT unit_price_vnd FROM ticketing.booking_items WHERE booking_id=?",Integer.class,id));UUID next=ga(1);assertEquals(300000,jdbc.queryForObject("SELECT total_amount_vnd FROM ticketing.bookings WHERE id=?",Integer.class,next));
    }
    @Test void recordingFailureRollsBackLedgerVersionAndResultOutbox(){
        UUID id=ga(2);jdbc.execute("CREATE FUNCTION ticketing.it_fail_record() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.aggregate_id='"+id+"' AND NEW.type='PaymentResultRecorded' THEN RAISE EXCEPTION 'record rollback'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER it_fail_record BEFORE INSERT ON ticketing.outbox_events FOR EACH ROW EXECUTE FUNCTION ticketing.it_fail_record()");
        try{code(500,"INTERNAL_ERROR",()->record(id,"SUCCESS"));assertEquals(0,rows("payments",id));assertEquals(1,rows("outbox_events",id));assertEquals(2,held());assertEquals(1,jdbc.queryForObject("SELECT aggregate_version FROM ticketing.bookings WHERE id=?",Integer.class,id));}
        finally{jdbc.execute("DROP TRIGGER it_fail_record ON ticketing.outbox_events");jdbc.execute("DROP FUNCTION ticketing.it_fail_record()");}
    }
    @Test void ticketFailureRollsBackConfirmationButKeepsDurableResultForRetry() throws Exception {
        UUID id=seat(2);var in=input("SUCCESS");jdbc.execute("CREATE FUNCTION ticketing.it_fail_ticket() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"+id+"' THEN RAISE EXCEPTION 'ticket rollback'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER it_fail_ticket AFTER INSERT ON ticketing.tickets FOR EACH ROW EXECUTE FUNCTION ticketing.it_fail_ticket()");
        try{assertEquals(500,simulate(id,in).statusCode());assertEquals("PENDING_PAYMENT",state(id));assertEquals(1,rows("payments",id));assertEquals(0,rows("tickets",id));assertEquals(0,count("BookingConfirmed",id));assertEquals(1,rows("booking_status_history",id));assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE held_by_booking_id=? AND status='HELD'",Integer.class,id));}
        finally{jdbc.execute("DROP TRIGGER it_fail_ticket ON ticketing.tickets");jdbc.execute("DROP FUNCTION ticketing.it_fail_ticket()");}
        assertEquals(202,simulate(id,in).statusCode());assertEquals(1,rows("payments",id));assertEquals("CONFIRMED",state(id));assertEquals(2,rows("tickets",id));assertEquals(1,count("PaymentResultRecorded",id));assertEquals(1,count("BookingConfirmed",id));
    }
    @Test void compensationFailureRollsBackLateExpiryReleaseAndBothTransitions(){
        UUID id=ga(2);due(id);var result=record(id,"SUCCESS");jdbc.execute("CREATE FUNCTION ticketing.it_fail_compensation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"+id+"' THEN RAISE EXCEPTION 'compensation rollback'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER it_fail_compensation BEFORE INSERT ON ticketing.payment_compensations FOR EACH ROW EXECUTE FUNCTION ticketing.it_fail_compensation()");
        try{code(500,"INTERNAL_ERROR",()->processor.process(id,result.paymentId()));assertEquals("PENDING_PAYMENT",state(id));assertEquals(2,held());assertEquals(0,count("BookingExpired",id));assertEquals(0,count("BookingRefundRequired",id));assertEquals(0,rows("payment_compensations",id));}
        finally{jdbc.execute("DROP TRIGGER it_fail_compensation ON ticketing.payment_compensations");jdbc.execute("DROP FUNCTION ticketing.it_fail_compensation()");}
        processor.process(id,result.paymentId());assertEquals("REFUND_REQUIRED",state(id));assertEquals(0,held());assertEquals(1,rows("payment_compensations",id));
    }
    @Test void processingSeatLockTimeoutLeavesNoPartialSaleOrTickets() throws Exception {
        UUID id=seat(2);var result=record(id,"SUCCESS");
        try(var c=connection()){c.setAutoCommit(false);try(var s=c.createStatement()){s.executeQuery("SELECT id FROM ticketing.seats WHERE id='"+seats.get(1)+"' FOR UPDATE");}
            code(503,"BOOKING_BUSY",()->processor.process(id,result.paymentId()));assertEquals("PENDING_PAYMENT",state(id));assertEquals(0,rows("tickets",id));assertEquals(0,count("BookingConfirmed",id));assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE held_by_booking_id=? AND status='HELD'",Integer.class,id));c.rollback();}
        processor.process(id,result.paymentId());assertEquals("CONFIRMED",state(id));assertEquals(2,rows("tickets",id));
    }
    @Test void ticketConstraintsRejectDuplicateOrdinalAndWrongBookingItem(){
        UUID id=ga(2);pay(id,"SUCCESS");var ticket=tickets.list(owner,id).getFirst();UUID another=ga(1);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO ticketing.tickets(id,booking_id,booking_item_id,ticket_ordinal,code) VALUES (?,?,?,?,'duplicate-test')",UUID.randomUUID(),id,ticket.bookingItemId(),ticket.ticketOrdinal()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO ticketing.tickets(id,booking_id,booking_item_id,ticket_ordinal,code) VALUES (?,?,?,1,'wrong-item')",UUID.randomUUID(),another,ticket.bookingItemId()));assertEquals(2,rows("tickets",id));
    }
    @Test void failureHistoryFaultRollsBackReleaseAndCanBeProcessedAgain(){
        UUID id=ga(3);var result=record(id,"FAILURE");
        jdbc.execute("CREATE FUNCTION ticketing.it_fail_payment_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"+id+"' AND NEW.to_status='PAYMENT_FAILED' THEN RAISE EXCEPTION 'failure rollback'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_fail_payment_failure AFTER INSERT ON ticketing.booking_status_history FOR EACH ROW EXECUTE FUNCTION ticketing.it_fail_payment_failure()");
        try{code(500,"INTERNAL_ERROR",()->processor.process(id,result.paymentId()));assertEquals("PENDING_PAYMENT",state(id));assertEquals(3,held());assertEquals(0,sold());assertEquals(0,count("BookingPaymentFailed",id));assertEquals(1,rows("booking_status_history",id));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticketing.payments WHERE id=? AND processed_at IS NULL",Integer.class,result.paymentId()));}
        finally{jdbc.execute("DROP TRIGGER it_fail_payment_failure ON ticketing.booking_status_history");jdbc.execute("DROP FUNCTION ticketing.it_fail_payment_failure()");}
        assertEquals(1,processor.process(id,result.paymentId()));assertEquals(0,processor.process(id,result.paymentId()));assertEquals("PAYMENT_FAILED",state(id));assertEquals(0,held());assertEquals(1,count("BookingPaymentFailed",id));
    }
    @Test void concurrentAdminPriceAndAvailabilityChangeCannotInvalidatePaidSeatHold() throws Exception {
        UUID id=seat(2);var result=record(id,"SUCCESS");String patch=json.writeValueAsString(Map.of("ticketTypes",List.of(Map.of("id",seated,"priceVnd",500000,"active",false))));
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var payment=pool.submit(()->{gate.await();return processor.process(id,result.paymentId());});var admin=pool.submit(()->{gate.await();return send("PATCH","/api/admin/events/"+event,patch,"demo-admin-token");});gate.countDown();assertEquals(1,payment.get(15,TimeUnit.SECONDS));assertEquals(200,admin.get(15,TimeUnit.SECONDS).statusCode());
        }
        assertEquals("CONFIRMED",state(id));assertEquals(400000,jdbc.queryForObject("SELECT total_amount_vnd FROM ticketing.bookings WHERE id=?",Integer.class,id));assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.booking_items WHERE booking_id=? AND unit_price_vnd=200000",Integer.class,id));assertEquals(500000,jdbc.queryForObject("SELECT price_vnd FROM ticketing.ticket_types WHERE id=?",Integer.class,seated));
        assertEquals(ExpiryService.Outcome.NOT_DUE,expiry.expire(id));assertEquals(2,rows("tickets",id));assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE held_by_booking_id=? AND status='SOLD'",Integer.class,id));
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @Autowired BookingLifecycle lifecycle;
    @Autowired com.haolo.ticketing.messaging.OutboxWriter outbox;
    @Autowired com.haolo.ticketing.identity.DemoSettings settings;
    @Test void recordingAndProcessingShareOneDeadlineAndRetryTheDurableResult(){
        UUID id=ga(2);var command=input("SUCCESS");
        var shortBudget=new BookingTransactions(manager,new BookingPolicy(100,1900,2000,3500,1000,600,6),jdbc);
        var service=new PaymentSimulatorService(jdbc,shortBudget,lifecycle,processor,outbox,settings);
        jdbc.execute("CREATE FUNCTION ticketing.it_slow_record() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.aggregate_id='"+id+"' AND NEW.type='PaymentResultRecorded' THEN PERFORM pg_sleep(0.8); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_slow_record AFTER INSERT ON ticketing.outbox_events FOR EACH ROW EXECUTE FUNCTION ticketing.it_slow_record()");
        try{code(503,"BOOKING_BUSY",()->service.simulate(owner,id,command,UUID.randomUUID()));assertEquals(1,rows("payments",id));assertEquals(1,count("PaymentResultRecorded",id));assertEquals("PENDING_PAYMENT",state(id));assertEquals(2,held());assertEquals(0,rows("tickets",id));}
        finally{jdbc.execute("DROP TRIGGER it_slow_record ON ticketing.outbox_events");jdbc.execute("DROP FUNCTION ticketing.it_slow_record()");}
        simulator.simulate(owner,id,command,UUID.randomUUID());assertEquals("CONFIRMED",state(id));assertEquals(1,rows("payments",id));assertEquals(2,rows("tickets",id));assertEquals(1,count("BookingConfirmed",id));
    }
    @AfterAll static void writeEvidence() throws Exception {Files.writeString(Path.of("target/part6-evidence.json"),new ObjectMapper().writeValueAsString(evidence));}
}
