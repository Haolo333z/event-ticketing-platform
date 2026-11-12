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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.messaging.OutboxWriter;
import com.haolo.ticketing.shared.ApiException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=${PG_IT_URL}","expiry.enabled=false"})
@ActiveProfiles("test") @EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+")
class PostgresBookingTest {
    @Autowired JdbcTemplate jdbc;@Autowired BookingCommandService commands;@Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager manager;@Autowired OutboxWriter outbox;
    @LocalServerPort int port;
    UUID event,type;List<DemoPrincipal> customers;static final List<Map<String,Object>> evidence=new ArrayList<>();
    final DemoPrincipal owner=new DemoPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"),"CUSTOMER");
    final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void fixture(){
        event=UUID.randomUUID();type=UUID.randomUUID();customers=new ArrayList<>();customers.add(owner);
        for(int i=0;i<4;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO ticketing.users(id,demo_role,display_name) VALUES (?,'CUSTOMER','Concurrent test')",id);customers.add(new DemoPrincipal(id,"CUSTOMER"));}
        jdbc.update("INSERT INTO ticketing.events(id,title,description,venue,sale_start_at,sale_end_at,starts_at,status) VALUES (?,'GA test','','Local',clock_timestamp()-interval '1h',clock_timestamp()+interval '1h',clock_timestamp()+interval '2h','PUBLISHED')",event);
        jdbc.update("INSERT INTO ticketing.ticket_types(id,event_id,name,mode,price_vnd) VALUES (?,?,'GA','GA',250000)",type,event);
        jdbc.update("INSERT INTO ticketing.inventory_ga(ticket_type_id,capacity) VALUES (?,10)",type);
    }
    @AfterEach void cleanup(){
        for(String table:List.of("idempotency_keys","booking_status_history","outbox_events")){
            String col=table.equals("outbox_events")?"aggregate_id":"booking_id";
            jdbc.update("DELETE FROM ticketing."+table+" WHERE "+col+" IN (SELECT id FROM ticketing.bookings WHERE event_id=?)",event);
        }
        jdbc.update("DELETE FROM ticketing.booking_items WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.bookings WHERE event_id=?",event);
        jdbc.update("DELETE FROM ticketing.inventory_ga WHERE ticket_type_id=?",type);jdbc.update("DELETE FROM ticketing.ticket_types WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.events WHERE id=?",event);
        for(var customer:customers)if(!customer.equals(owner))jdbc.update("DELETE FROM ticketing.users WHERE id=?",customer.accountId());
    }
    GaBookingRequest request(int q){return new GaBookingRequest(event,type,q);}
    BookingCommandService.Result book(String key,int quantity){return commands.create(owner,key,request(quantity),UUID.randomUUID());}
    String body(int q){return json.writeValueAsString(Map.of("eventId",event,"ticketTypeId",type,"quantity",q));}
    HttpResponse<String> send(String method,String path,String body,String key,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
        if(token!=null)builder.header("Authorization","Bearer "+token);if(key!=null)builder.header("Idempotency-Key",key);
        return http.send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String key,int q) throws Exception{return send("POST","/api/bookings",body(q),key,"demo-customer-token");}
    int held(){return jdbc.queryForObject("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,type);}
    int bookings(){return jdbc.queryForObject("SELECT count(*) FROM ticketing.bookings WHERE event_id=?",Integer.class,event);}
    int rows(String table){String col=table.equals("outbox_events")?"aggregate_id":"booking_id";return jdbc.queryForObject("SELECT count(*) FROM ticketing."+table+" WHERE "+col+" IN (SELECT id FROM ticketing.bookings WHERE event_id=?)",Integer.class,event);}
    Connection connection() throws Exception{return DriverManager.getConnection(System.getenv("PG_IT_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"));}
    @Test void successLocationPollingOwnerAndHistory() throws Exception {
        var r=post("success-"+event,2);assertEquals(202,r.statusCode(),r.body());var result=json.readTree(r.body());
        String location=r.headers().firstValue("Location").orElseThrow();assertEquals("/api/bookings/"+result.get("bookingId").asText(),location);
        assertEquals("PENDING_PAYMENT",result.get("status").asText());assertEquals(500000,result.get("totalAmountVnd").asLong());assertEquals(2,held());
        var view=send("GET",location,"",null,"demo-customer-token");assertEquals(200,view.statusCode());var detail=json.readTree(view.body());
        assertTrue(detail.get("expiredAt").isNull());assertEquals(1,detail.get("history").size());assertTrue(detail.get("history").get(0).get("fromStatus").isNull());
        assertEquals("PENDING_PAYMENT",detail.get("history").get(0).get("toStatus").asText());
        assertEquals(1,rows("booking_items"));assertEquals(1,rows("booking_status_history"));assertEquals(1,rows("outbox_events"));
        assertEquals(600,jdbc.queryForObject("SELECT extract(epoch FROM expires_at-created_at)::int FROM ticketing.bookings WHERE event_id=?",Integer.class,event));
        assertEquals(0,jdbc.queryForObject("SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,type));
        var mine=send("GET","/api/me/bookings","",null,"demo-customer-token");assertEquals(200,mine.statusCode());assertTrue(json.readTree(mine.body()).get("items").toString().contains(result.get("bookingId").asText()));
        assertEquals(400,send("GET","/api/me/bookings?size=101","",null,"demo-customer-token").statusCode());
    }
    @Test void validationAndRoles() throws Exception {
        assertEquals(401,send("POST","/api/bookings",body(1),"auth",null).statusCode());
        assertEquals(403,send("POST","/api/bookings",body(1),"auth","demo-admin-token").statusCode());
        assertEquals(400,post(null,1).statusCode());assertEquals(400,post("bad key",1).statusCode());
        for(String b:List.of(body(0),body(7),body(1).replace("\"quantity\":1","\"quantity\":1.5"),body(1).replace("\"quantity\":1","\"quantity\":1,\"userId\":\"fake\""),"{\"eventId\":\"bad\"}",body(1).replace(event.toString(),"1-1-1-1-1")))
            assertEquals(400,send("POST","/api/bookings",b,"invalid", "demo-customer-token").statusCode(),b);
        assertEquals(0,bookings());assertEquals(0,held());
    }
    @Test void foreignCustomerCannotReadBookingButAdminCan() throws Exception {
        var foreign=commands.create(customers.get(1),"foreign",request(1),UUID.randomUUID());String path="/api/bookings/"+foreign.bookingId();
        assertEquals(404,send("GET",path,"",null,"demo-customer-token").statusCode());
        assertEquals(200,send("GET",path,"",null,"demo-admin-token").statusCode());
        assertFalse(send("GET","/api/me/bookings","",null,"demo-customer-token").body().contains(foreign.bookingId().toString()));
        assertEquals(403,send("GET","/api/me/bookings","",null,"demo-admin-token").statusCode());
    }
    @Test void sequentialReplayAndChangedPayloadConflict() throws Exception {
        String key="idem-"+event;var a=post(key,2);var b=post(key,2);assertEquals(202,a.statusCode(),a.body());assertEquals(a.body(),b.body());assertEquals(a.headers().firstValue("Location"),b.headers().firstValue("Location"));
        String equivalent="{\"quantity\":2.0,\"ticketTypeId\":\""+type.toString().toUpperCase(Locale.ROOT)+"\",\"eventId\":\""+event.toString().toUpperCase(Locale.ROOT)+"\"}";
        assertEquals(a.body(),send("POST","/api/bookings",equivalent,key,"demo-customer-token").body());
        var changed=post(key,3);assertEquals(409,changed.statusCode());assertEquals("IDEMPOTENCY_KEY_REUSED",json.readTree(changed.body()).get("code").asText());
        assertEquals(2,held());assertEquals(1,bookings());assertEquals(1,rows("idempotency_keys"));
        commands.create(customers.get(1),key,request(1),UUID.randomUUID());assertEquals(3,held());assertEquals(2,bookings());
    }
    @Test void inventoryConflictRollsBackAndSameKeyCanRetry() throws Exception {
        jdbc.update("UPDATE ticketing.inventory_ga SET capacity=1 WHERE ticket_type_id=?",type);
        String key="soldout-"+event;var conflict=post(key,2);assertEquals(409,conflict.statusCode());assertEquals("INVENTORY_UNAVAILABLE",json.readTree(conflict.body()).get("code").asText());
        assertEquals(0,held());assertEquals(0,bookings());assertEquals(0,rows("outbox_events"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ticketing.idempotency_keys WHERE key=?",Integer.class,key));
        jdbc.update("UPDATE ticketing.inventory_ga SET capacity=2 WHERE ticket_type_id=?",type);assertEquals(202,post(key,2).statusCode());
    }
    @Test void saleWindowInactiveAndSeatModeAreRejected() throws Exception {
        jdbc.update("UPDATE ticketing.ticket_types SET active=false WHERE id=?",type);assertEquals(409,post("inactive",1).statusCode());
        jdbc.update("UPDATE ticketing.ticket_types SET active=true,mode='SEATED' WHERE id=?",type);assertEquals(400,post("seated",1).statusCode());
        jdbc.update("UPDATE ticketing.ticket_types SET mode='GA' WHERE id=?",type);
        jdbc.update("UPDATE ticketing.events SET sale_start_at=clock_timestamp()+interval '1m' WHERE id=?",event);assertEquals(409,post("notopen",1).statusCode());
        jdbc.update("UPDATE ticketing.events SET status='DRAFT' WHERE id=?",event);assertEquals(404,post("draft",1).statusCode());assertEquals(0,held());assertEquals(0,bookings());
    }
    @Test void replayAfterExpiredKeepsOriginal202() throws Exception {
        String key="expired-"+event;var original=post(key,2);UUID id=UUID.fromString(json.readTree(original.body()).get("bookingId").asText());
        new TransactionTemplate(manager).execute(status->{
            jdbc.update("UPDATE ticketing.bookings SET status='EXPIRED',expired_at=clock_timestamp(),aggregate_version=2 WHERE id=?",id);
            jdbc.update("UPDATE ticketing.inventory_ga SET held_count=held_count-2 WHERE ticket_type_id=?",type);
            String at=jdbc.queryForObject("SELECT expired_at FROM ticketing.bookings WHERE id=?",(r,n)->r.getTimestamp(1).toInstant().toString(),id);
            outbox.append(id,2,"BookingExpired",UUID.randomUUID(),Map.of("bookingId",id.toString(),"expiredAt",at));
            jdbc.update("INSERT INTO ticketing.booking_status_history(id,booking_id,from_status,to_status) VALUES (?,?,'PENDING_PAYMENT','EXPIRED')",UUID.randomUUID(),id);return null;
        });
        jdbc.update("UPDATE ticketing.events SET status='CLOSED' WHERE id=?",event);
        assertEquals(original.body(),post(key,2).body());assertEquals(0,held());
        assertEquals("EXPIRED",json.readTree(send("GET","/api/bookings/"+id,"",null,"demo-customer-token").body()).get("status").asText());
    }
    @Test void inventoryLockTimeoutRollsBackAllThenRetrySucceeds() throws Exception {
        String key="inventory-timeout-"+event;
        try(var c=connection()){c.setAutoCommit(false);try(var s=c.createStatement()){s.executeQuery("SELECT * FROM ticketing.inventory_ga WHERE ticket_type_id='"+type+"' FOR UPDATE");}
            var response=post(key,1);assertEquals(503,response.statusCode(),response.body());assertEquals("1",response.headers().firstValue("Retry-After").orElseThrow());
            assertEquals("BOOKING_BUSY",json.readTree(response.body()).get("code").asText());assertEquals(0,bookings());assertEquals(0,rows("outbox_events"));c.rollback();
        }
        assertEquals(202,post(key,1).statusCode());assertEquals(1,held());
    }
    @Test void uniqueKeyTimeoutIsNotMisclassifiedAndRetryIsSafe() throws Exception {
        String key="key-timeout-"+event;
        try(var c=connection()){c.setAutoCommit(false);try(var s=c.prepareStatement("INSERT INTO ticketing.idempotency_keys(account_id,operation,key,request_hash) VALUES (?,'CREATE_BOOKING',?,?)")){s.setObject(1,owner.accountId());s.setString(2,key);s.setString(3,request(1).hash());s.executeUpdate();}
            var r=post(key,1);assertEquals(503,r.statusCode(),r.body());assertEquals("BOOKING_BUSY",json.readTree(r.body()).get("code").asText());assertEquals(0,bookings());assertEquals(0,held());c.rollback();
        }
        assertEquals(202,post(key,1).statusCode());assertEquals(1,held());
    }
    @Test void cannotCommitIncompleteReservation() throws Exception {
        String key="incomplete-"+event;
        try(var c=connection()){c.setAutoCommit(false);try(var s=c.prepareStatement("INSERT INTO ticketing.idempotency_keys(account_id,operation,key,request_hash) VALUES (?,'CREATE_BOOKING',?,?)")){s.setObject(1,owner.accountId());s.setString(2,key);s.setString(3,request(1).hash());s.executeUpdate();}
            assertThrows(SQLException.class,c::commit);c.rollback();
        }
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ticketing.idempotency_keys WHERE key=?",Integer.class,key));
    }
    @Test void failureAfterOutboxRollsBackEveryWrite() throws Exception {
        String key="forced-"+event;
        jdbc.execute("CREATE FUNCTION ticketing.it_fail_booking() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.key='"+key+"' THEN RAISE EXCEPTION 'forced rollback'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_fail_booking BEFORE UPDATE ON ticketing.idempotency_keys FOR EACH ROW EXECUTE FUNCTION ticketing.it_fail_booking()");
        try {assertEquals(500,post(key,2).statusCode());assertEquals(0,bookings());assertEquals(0,held());assertEquals(0,rows("booking_items"));assertEquals(0,rows("booking_status_history"));assertEquals(0,rows("outbox_events"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ticketing.idempotency_keys WHERE key=?",Integer.class,key));}
        finally {jdbc.execute("DROP TRIGGER it_fail_booking ON ticketing.idempotency_keys");jdbc.execute("DROP FUNCTION ticketing.it_fail_booking()");}
        assertEquals(202,post(key,2).statusCode());assertEquals(2,held());
    }
    @Test void fiftyRequestsAcrossFiveCustomersCannotOversell() throws Exception {
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var start=new CountDownLatch(1);List<Future<Integer>> futures=new ArrayList<>();
            for(int i=0;i<50;i++){final int n=i;futures.add(executor.submit(()->{start.await();try {commands.create(customers.get(n%5),"burst-"+event+"-"+n,request(1),UUID.randomUUID());return 202;}catch(ApiException ex){return ex.status;}}));}
            start.countDown();int success=0,conflict=0;for(var future:futures){int status=future.get(20,TimeUnit.SECONDS);if(status==202)success++;else if(status==409)conflict++;else fail("Unexpected status "+status);}
            assertEquals(10,success);assertEquals(40,conflict);assertEquals(10,held());assertEquals(10,bookings());assertEquals(10,rows("outbox_events"));assertEquals(10,rows("idempotency_keys"));
            assertEquals(10,jdbc.queryForObject("SELECT sum(quantity)::int FROM ticketing.booking_items WHERE event_id=?",Integer.class,event));
            var item=Map.<String,Object>of("scenario","GA-01","requests",50,"customers",5,"capacity",10,"success",success,"conflict",conflict,"held",held(),"bookings",bookings());evidence.add(item);System.out.println("PART4 EVIDENCE "+json.writeValueAsString(item));
        }
    }
    @Test void twentyConcurrentDuplicatesCreateExactlyOneBooking() throws Exception {
        String key="duplicates-"+event;
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var start=new CountDownLatch(1);List<Future<BookingCommandService.Result>> futures=new ArrayList<>();
            for(int i=0;i<20;i++)futures.add(executor.submit(()->{start.await();return book(key,2);}));start.countDown();
            Set<UUID> ids=new HashSet<>();Set<String> bodies=new HashSet<>();for(var future:futures){var result=future.get(20,TimeUnit.SECONDS);ids.add(result.bookingId());bodies.add(result.body());}
            assertEquals(1,ids.size());assertEquals(1,bodies.size());assertEquals(2,held());assertEquals(1,bookings());assertEquals(1,rows("outbox_events"));
            var item=Map.<String,Object>of("scenario","IDEM-01","requests",20,"distinctBookingIds",ids.size(),"distinctBodies",bodies.size(),"held",held(),"outbox",rows("outbox_events"));evidence.add(item);System.out.println("PART4 EVIDENCE "+json.writeValueAsString(item));
        }
    }
    @Test void fiftyConcurrentHttpRequestsAlsoCannotOversell() throws Exception {
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var start=new CountDownLatch(1);List<Future<HttpResponse<String>>> futures=new ArrayList<>();
            for(int i=0;i<50;i++){int n=i;futures.add(executor.submit(()->{start.await();return post("http-burst-"+event+"-"+n,1);}));}
            start.countDown();int success=0,conflict=0;
            for(var future:futures){var result=future.get(20,TimeUnit.SECONDS);if(result.statusCode()==202)success++;else if(result.statusCode()==409){assertEquals("INVENTORY_UNAVAILABLE",json.readTree(result.body()).get("code").asText());conflict++;}else fail(result.statusCode()+" "+result.body());}
            assertEquals(10,success);assertEquals(40,conflict);assertEquals(10,held());assertEquals(10,bookings());assertEquals(10,rows("outbox_events"));
            var item=Map.<String,Object>of("scenario","GA-01-HTTP","requests",50,"capacity",10,"success",success,"conflict",conflict,"held",held(),"bookings",bookings());evidence.add(item);System.out.println("PART4 EVIDENCE "+json.writeValueAsString(item));
        }
    }
    @Test void concurrentDifferentPayloadsWithSameKeyConflict() throws Exception {
        String key="different-"+event;
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var gate=new CountDownLatch(1);var futures=new ArrayList<Future<Integer>>();
            for(int q=1;q<=2;q++){int quantity=q;futures.add(executor.submit(()->{gate.await();try{book(key,quantity);return 202;}catch(ApiException ex){assertEquals("IDEMPOTENCY_KEY_REUSED",ex.code);return ex.status;}}));}
            gate.countDown();var codes=new ArrayList<Integer>();for(var f:futures)codes.add(f.get(10,TimeUnit.SECONDS));Collections.sort(codes);assertEquals(List.of(202,409),codes);assertEquals(1,bookings());assertTrue(held()==1||held()==2);
        }
    }
    @Test void serializationRetriesAreBoundedAndDoNotDoubleHold() throws Exception {
        String recover="retry-"+event,exhaust="exhaust-"+event;
        jdbc.execute("CREATE SEQUENCE ticketing.it_retry_sequence");
        jdbc.execute("CREATE FUNCTION ticketing.it_retry_booking() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.key='"+recover+"' THEN IF nextval('ticketing.it_retry_sequence')=1 THEN RAISE EXCEPTION 'retry fixture' USING ERRCODE='40001'; END IF; ELSIF NEW.key='"+exhaust+"' THEN PERFORM nextval('ticketing.it_retry_sequence'); RAISE EXCEPTION 'exhaust fixture' USING ERRCODE='40001'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER it_retry_booking BEFORE UPDATE ON ticketing.idempotency_keys FOR EACH ROW EXECUTE FUNCTION ticketing.it_retry_booking()");
        try {
            assertEquals(202,post(recover,1).statusCode());assertEquals(2,jdbc.queryForObject("SELECT last_value FROM ticketing.it_retry_sequence",Long.class));
            assertEquals(503,post(exhaust,1).statusCode());assertEquals(5,jdbc.queryForObject("SELECT last_value FROM ticketing.it_retry_sequence",Long.class));
            assertEquals(1,held());assertEquals(1,bookings());assertEquals(1,rows("outbox_events"));
        }finally{jdbc.execute("DROP TRIGGER it_retry_booking ON ticketing.idempotency_keys");jdbc.execute("DROP FUNCTION ticketing.it_retry_booking()");jdbc.execute("DROP SEQUENCE ticketing.it_retry_sequence");}
    }
    @AfterAll static void report() throws Exception {Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target","part4-evidence.json"),new tools.jackson.databind.json.JsonMapper().writeValueAsString(evidence));}
}
