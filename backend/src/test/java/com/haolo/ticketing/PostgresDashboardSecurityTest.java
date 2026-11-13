package com.haolo.ticketing;

import java.util.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.identity.*;
import com.haolo.ticketing.events.AdminCatalogQuery;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.operations.ReplayService;
import com.haolo.ticketing.shared.ApiException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=${PG_IT_URL}","expiry.enabled=false","messaging.enabled=false"})
@ActiveProfiles("test") @EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+") @ExtendWith(OutputCaptureExtension.class)
class PostgresDashboardSecurityTest {
    @Autowired JdbcTemplate jdbc;@Autowired BookingCommandService bookings;@Autowired BookingCancelService cancel;
    @Autowired BookingQueryService bookingQuery;@Autowired AdminCatalogQuery catalog;@Autowired ReplayService replay;@Autowired ObjectMapper json;
    @LocalServerPort int port;final HttpClient http=HttpClient.newHttpClient();
    final DemoPrincipal owner=new DemoPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"),"CUSTOMER");
    final DemoPrincipal admin=new DemoPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000002"),"ADMIN");
    UUID event,type,seatType,foreign;DemoPrincipal other;
    @BeforeEach void fixture(){
        event=UUID.randomUUID();type=UUID.randomUUID();seatType=UUID.randomUUID();foreign=UUID.randomUUID();other=new DemoPrincipal(foreign,"CUSTOMER");
        jdbc.update("INSERT INTO ticketing.users(id,demo_role,display_name) VALUES (?,'CUSTOMER','T7 fixture')",foreign);
        jdbc.update("INSERT INTO ticketing.events(id,title,description,venue,sale_start_at,sale_end_at,starts_at,status) VALUES (?,'T7 private','','Local',clock_timestamp()-interval '1h',clock_timestamp()+interval '1h',clock_timestamp()+interval '2h','DRAFT')",event);
        jdbc.update("INSERT INTO ticketing.ticket_types(id,event_id,name,mode,price_vnd) VALUES (?,?,'GA','GA',100000),(?,?,'Seats','SEATED',200000)",type,event,seatType,event);
        jdbc.update("INSERT INTO ticketing.inventory_ga(ticket_type_id,capacity) VALUES (?,6)",type);
        jdbc.update("INSERT INTO ticketing.seats(id,event_id,ticket_type_id,label) VALUES (?,?,?,'A1')",UUID.randomUUID(),event,seatType);
    }
    @AfterEach void cleanup(){
        for(String table:List.of("idempotency_keys","booking_status_history","outbox_events"))jdbc.update("DELETE FROM ticketing."+table+" WHERE "+(table.equals("outbox_events")?"aggregate_id":"booking_id")+" IN(SELECT id FROM ticketing.bookings WHERE event_id=?)",event);
        jdbc.update("DELETE FROM ticketing.booking_items WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.seats WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.bookings WHERE event_id=?",event);
        jdbc.update("DELETE FROM ticketing.inventory_ga WHERE ticket_type_id=?",type);jdbc.update("DELETE FROM ticketing.ticket_types WHERE event_id=?",event);jdbc.update("DELETE FROM ticketing.events WHERE id=?",event);jdbc.update("DELETE FROM ticketing.users WHERE id=?",foreign);
    }
    void publish(){jdbc.update("UPDATE ticketing.events SET status='PUBLISHED' WHERE id=?",event);}
    UUID book(DemoPrincipal who,int quantity){publish();return bookings.create(who,UUID.randomUUID().toString(),new GaBookingRequest(event,type,quantity),UUID.randomUUID()).bookingId();}
    HttpResponse<String> request(String method,String path,byte[] body,String token,Map<String,String> extra)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)b.header("Authorization","Bearer "+token);extra.forEach(b::header);
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path,String token)throws Exception{return request("GET",path,null,token,Map.of());}
    HttpResponse<String> post(String path,String body,String token)throws Exception{return request("POST",path,body.getBytes(StandardCharsets.UTF_8),token,Map.of("Content-Type","application/json"));}
    void error(HttpResponse<String> r,int status,String code){assertEquals(status,r.statusCode(),r.body());var n=json.readTree(r.body());assertEquals(code,n.path("code").asString());assertEquals(Set.of("code","message","correlationId"),new HashSet<>(n.propertyNames()));assertDoesNotThrow(()->UUID.fromString(n.path("correlationId").asString()));assertEquals(n.path("correlationId").asString(),r.headers().firstValue("X-Correlation-Id").orElseThrow());if(status==503)assertEquals("1",r.headers().firstValue("Retry-After").orElseThrow());}
    @Test void roleAndIdentityAreNeverTakenFromClient()throws Exception{
        for(String path:List.of("/api/admin/events","/api/admin/events/"+event+"/inventory","/api/admin/bookings","/api/admin/operations/outbox")){
            error(get(path,null),401,"UNAUTHENTICATED");error(get(path,"unknown"),401,"UNAUTHENTICATED");error(get(path,"demo-customer-token"),403,"FORBIDDEN");
            error(request("GET",path+"?role=ADMIN&userId="+admin.accountId(),null,"demo-customer-token",Map.of("X-Role","ADMIN","X-User-Id",admin.accountId().toString())),403,"FORBIDDEN");assertEquals(200,get(path,"demo-admin-token").statusCode());
        }
        assertThrows(ApiException.class,()->catalog.list(owner,0,20));assertThrows(ApiException.class,()->catalog.inventory(owner,event));assertThrows(ApiException.class,()->bookingQuery.admin(owner,0,20,null));
        UUID mine=book(owner,1),theirs=book(other,1);error(get("/api/bookings/"+theirs,"demo-customer-token"),404,"NOT_FOUND");assertEquals(200,get("/api/bookings/"+theirs,"demo-admin-token").statusCode());
        String history=get("/api/me/bookings?userId="+foreign,"demo-customer-token").body();assertTrue(history.contains(mine.toString()));assertFalse(history.contains(theirs.toString()));
    }
    @Test void privateCatalogAndInactiveInventoryStayAdminOnly()throws Exception{
        error(get("/api/events/"+event,null),404,"NOT_FOUND");error(get("/api/events/"+event+"/availability",null),404,"NOT_FOUND");
        assertTrue(get("/api/admin/events?size=100","demo-admin-token").body().contains(event.toString()));
        jdbc.update("UPDATE ticketing.ticket_types SET active=false WHERE id=?",type);
        var stock=json.readTree(get("/api/admin/events/"+event+"/inventory","demo-admin-token").body());assertEquals(2,stock.path("ticketTypes").size());
        var ga=stock.path("ticketTypes").valueStream().filter(n->n.path("mode").asString().equals("GA")).findFirst().orElseThrow();assertEquals(6,ga.path("capacity").intValue());assertEquals(0,ga.path("held").intValue());assertEquals(6,ga.path("available").intValue());
        assertFalse(stock.toString().contains("owner"));assertFalse(stock.toString().contains("accountId"));
        jdbc.update("UPDATE ticketing.events SET status='CLOSED' WHERE id=?",event);assertEquals(200,get("/api/admin/events/"+event+"/inventory","demo-admin-token").statusCode());
        error(get("/api/admin/events/"+UUID.randomUUID()+"/inventory","demo-admin-token"),404,"NOT_FOUND");
    }
    @Test void adminBookingFilterPaginationAndQueryValidation()throws Exception{
        // A different event deliberately contributes data, even when this class runs alone.
        UUID background=bookings.create(owner,"pagination-background-"+UUID.randomUUID(),new GaBookingRequest(
            UUID.fromString("20000000-0000-4000-8000-000000000002"),UUID.fromString("30000000-0000-4000-8000-000000000002"),1),UUID.randomUUID()).bookingId();
        try{
            cancel.cancel(owner,background,UUID.randomUUID());
            UUID pending=book(owner,1),cancelled=book(other,1);cancel.cancel(other,cancelled,UUID.randomUUID());
            var ownIds=Set.of(pending,cancelled);
            assertEquals(ownIds,new HashSet<>(jdbc.queryForList("SELECT id FROM ticketing.bookings WHERE event_id=?",UUID.class,event)));
            // Target rows tie on time; the older background row also tests the primary time sort.
            var timestamp=jdbc.queryForObject("SELECT created_at FROM ticketing.bookings WHERE id=?",(r,n)->r.getTimestamp(1),pending);
            for(UUID id:ownIds)jdbc.update("UPDATE ticketing.bookings SET created_at=? WHERE id=?",timestamp,id);
            jdbc.update("UPDATE ticketing.bookings SET created_at=? WHERE id=?",java.sql.Timestamp.from(timestamp.toInstant().minusSeconds(1)),background);
            // Take an unordered independent DB snapshot; compute the expected order in Java.
            var snapshot=jdbc.query("SELECT id,event_id,status,created_at FROM ticketing.bookings",(r,n)->new ExpectedBooking(r.getObject("id",UUID.class),r.getObject("event_id",UUID.class),r.getString("status"),r.getTimestamp("created_at").toInstant()));
            var all=assertAdminBookingPages(snapshot,null);
            assertTrue(all.contains(background));
            assertEquals(ownIds,new HashSet<>(all.stream().filter(ownIds::contains).toList()));
            var cancelledIds=assertAdminBookingPages(snapshot,"CANCELLED");
            assertTrue(cancelledIds.containsAll(Set.of(cancelled,background)));assertFalse(cancelledIds.contains(pending));
            var pendingIds=assertAdminBookingPages(snapshot,"PENDING_PAYMENT");
            assertTrue(pendingIds.contains(pending));assertFalse(pendingIds.contains(cancelled));assertFalse(pendingIds.contains(background));
            for(String path:List.of("/api/admin/bookings?status=unknown","/api/admin/bookings?status=","/api/admin/bookings?page=-1","/api/admin/bookings?size=101","/api/admin/events?page=10001","/api/admin/events?size=0","/api/admin/events?size=x","/api/admin/events/not-uuid/inventory"))error(get(path,"demo-admin-token"),400,"VALIDATION_ERROR");
            assertEquals(0,json.readTree(get("/api/admin/events?page=10000","demo-admin-token").body()).path("items").size());
        }finally{
            // This background fixture is outside this class's usual event-scoped cleanup.
            jdbc.update("DELETE FROM ticketing.idempotency_keys WHERE booking_id=?",background);
            jdbc.update("DELETE FROM ticketing.booking_status_history WHERE booking_id=?",background);
            jdbc.update("DELETE FROM ticketing.outbox_events WHERE aggregate_id=?",background);
            jdbc.update("DELETE FROM ticketing.booking_items WHERE booking_id=?",background);
            jdbc.update("DELETE FROM ticketing.bookings WHERE id=?",background);
        }
    }
    private record ExpectedBooking(UUID id,UUID eventId,String status,Instant createdAt){}
    private List<UUID> assertAdminBookingPages(List<ExpectedBooking> snapshot,String status)throws Exception{
        var expected=snapshot.stream().filter(b->status==null||b.status().equals(status))
            .sorted(Comparator.comparing(ExpectedBooking::createdAt).thenComparing(b->b.id().toString()).reversed()).toList();
        var seen=new LinkedHashSet<UUID>();
        for(int page=0;page<=expected.size();page++){
            var response=get("/api/admin/bookings?size=1&page="+page+(status==null?"":"&status="+status),"demo-admin-token");
            assertEquals(200,response.statusCode(),response.body());var body=json.readTree(response.body());
            assertEquals(expected.size(),body.path("totalElements").longValue());assertEquals(page,body.path("page").intValue());assertEquals(1,body.path("size").intValue());
            if(page==expected.size()){assertEquals(0,body.path("items").size());continue;}
            assertEquals(1,body.path("items").size());var item=body.path("items").get(0);var row=expected.get(page);
            UUID id=UUID.fromString(item.path("bookingId").asString());assertTrue(seen.add(id),"Booking repeated across pages: "+id);
            assertEquals(row.id(),id,"Order must be created_at DESC,id DESC");assertEquals(row.eventId().toString(),item.path("eventId").asString());assertEquals(row.status(),item.path("status").asString());
        }
        assertEquals(expected.stream().map(ExpectedBooking::id).toList(),new ArrayList<>(seen));
        return new ArrayList<>(seen);
    }
    @Test void inventoryReadMatchesHeldAndCapacityConflictKeepsSnapshot()throws Exception{
        UUID booking=book(owner,2);var stock=json.readTree(get("/api/admin/events/"+event+"/inventory","demo-admin-token").body());var ga=stock.path("ticketTypes").valueStream().filter(n->n.path("mode").asString().equals("GA")).findFirst().orElseThrow();assertEquals(2,ga.path("held").intValue());assertEquals(4,ga.path("available").intValue());
        var body=json.writeValueAsString(Map.of("ticketTypes",List.of(Map.of("id",type.toString(),"capacity",1,"priceVnd",5))));error(request("PATCH","/api/admin/events/"+event,body.getBytes(StandardCharsets.UTF_8),"demo-admin-token",Map.of("Content-Type","application/json")),409,"CATALOG_CONFLICT");
        assertEquals(6,jdbc.queryForObject("SELECT capacity FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,type));assertEquals(200000,bookingQuery.get(owner,booking).totalAmountVnd());assertEquals(100000,jdbc.queryForObject("SELECT price_vnd FROM ticketing.ticket_types WHERE id=?",Long.class,type));
    }
    @Test void utf8BodySizeContentTypeDepthDuplicateAndTrailingJsonAreEnforced()throws Exception{
        String path="/api/bookings";for(String bad:List.of("[]","null","{}{}","{\"quantity\":1,\"quantity\":2}","{\"extra\":".repeat(11)+"0"+"}".repeat(11),"{\"broken\":"))error(post(path,bad,"demo-customer-token"),400,"VALIDATION_ERROR");
        error(post(path," ".repeat(8193),"demo-customer-token"),413,"PAYLOAD_TOO_LARGE");error(post(path,"é".repeat(4097),"demo-customer-token"),413,"PAYLOAD_TOO_LARGE");error(post(path," ".repeat(8192),"demo-customer-token"),400,"VALIDATION_ERROR");
        error(post("/api/admin/events"," ".repeat(131073),"demo-admin-token"),413,"PAYLOAD_TOO_LARGE");
        for(var headers:List.of(Map.of("Content-Type","text/plain"),Map.of("Content-Type","application/json;charset=ISO-8859-1"),Map.of("Content-Type","application/json","Content-Encoding","gzip")))error(request("POST",path,"{}".getBytes(StandardCharsets.UTF_8),"demo-customer-token",headers),415,"UNSUPPORTED_MEDIA_TYPE");
        error(request("POST",path,new byte[]{(byte)0xc3,0x28},"demo-customer-token",Map.of("Content-Type","application/json")),400,"VALIDATION_ERROR");error(request("GET","/api/events","{}".getBytes(StandardCharsets.UTF_8),null,Map.of()),400,"VALIDATION_ERROR");
    }
    @Test void forgedCommandFieldsAndInvalidNumbersCannotWrite()throws Exception{
        publish();String base="{\"eventId\":\""+event+"\",\"ticketTypeId\":\""+type+"\",\"quantity\":1";
        for(String extra:List.of(",\"userId\":\""+foreign+"\"",",\"role\":\"ADMIN\"",",\"totalAmountVnd\":1",",\"priceVnd\":0"))error(request("POST","/api/bookings",(base+extra+"}").getBytes(StandardCharsets.UTF_8),"demo-customer-token",Map.of("Content-Type","application/json","Idempotency-Key",UUID.randomUUID().toString())),400,"VALIDATION_ERROR");
        for(String number:List.of("-1","1.5","1000000000001","9223372036854775808"))error(request("PATCH","/api/admin/events/"+event,("{\"ticketTypes\":[{\"id\":\""+type+"\",\"priceVnd\":"+number+"}]}").getBytes(StandardCharsets.UTF_8),"demo-admin-token",Map.of("Content-Type","application/json")),400,"VALIDATION_ERROR");
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ticketing.bookings WHERE event_id=?",Integer.class,event));
    }
    @Test void corsAndCorrelationRejectUntrustedInput()throws Exception{
        for(String origin:List.of("https://example.invalid","null","http://127.0.0.1:5174"))error(request("OPTIONS","/api/admin/events",null,null,Map.of("Origin",origin,"Access-Control-Request-Method","POST")),403,"FORBIDDEN");
        var allowed=request("OPTIONS","/api/admin/events",null,null,Map.of("Origin","http://127.0.0.1:5173","Access-Control-Request-Method","POST","Access-Control-Request-Headers","authorization,content-type"));assertEquals(204,allowed.statusCode());assertEquals("http://127.0.0.1:5173",allowed.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());assertFalse(allowed.headers().firstValue("Access-Control-Allow-Credentials").isPresent());
        UUID correlation=UUID.randomUUID();var r=request("GET","/api/events/invalid",null,null,Map.of("X-Correlation-Id",correlation.toString()));error(r,400,"VALIDATION_ERROR");assertEquals(correlation.toString(),json.readTree(r.body()).path("correlationId").asString());
    }
    @Test void filterDatabaseErrorsStillUseSafeApiContract()throws Exception{
        jdbc.execute("ALTER TABLE ticketing.users RENAME TO users_auth_fault");try{error(get("/api/admin/events","demo-admin-token"),500,"INTERNAL_ERROR");}finally{jdbc.execute("ALTER TABLE ticketing.users_auth_fault RENAME TO users");}
    }
    @Test void invalidInputAndDatabaseFaultNeverLeakSecretMarkersToResponseOrLogs(CapturedOutput output)throws Exception{
        String marker="private-marker-"+UUID.randomUUID();var r=request("POST","/api/bookings",("{\"password\":\""+marker).getBytes(StandardCharsets.UTF_8),"demo-customer-token",Map.of("Content-Type","application/json","X-Correlation-Id",marker));error(r,400,"VALIDATION_ERROR");assertFalse(r.body().contains(marker));
        error(get("/api/events",marker),401,"UNAUTHENTICATED");
        try(var c=DriverManager.getConnection(System.getenv("PG_IT_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"));var s=c.createStatement()){
            s.execute("CREATE FUNCTION ticketing.t7_secret_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '"+marker+"'; END $$");
            s.execute("CREATE TRIGGER t7_secret_fault BEFORE UPDATE ON ticketing.events FOR EACH ROW EXECUTE FUNCTION ticketing.t7_secret_fault()");
            try{var fault=request("PATCH","/api/admin/events/"+event,"{\"title\":\"Fault fixture\"}".getBytes(StandardCharsets.UTF_8),"demo-admin-token",Map.of("Content-Type","application/json"));error(fault,500,"INTERNAL_ERROR");assertFalse(fault.body().contains(marker));}
            finally{s.execute("DROP TRIGGER t7_secret_fault ON ticketing.events");s.execute("DROP FUNCTION ticketing.t7_secret_fault()");}
        }
        assertFalse(output.getAll().contains(marker),"Sensitive marker reached logs");
    }
    @Test void unicodeReplayReasonUsesCodePointsNotUtf16(){
        UUID b=book(owner,1),e=jdbc.queryForObject("SELECT id FROM ticketing.outbox_events WHERE aggregate_id=?",UUID.class,b);
        assertEquals("ACCEPTED",replay.replay(admin,e,UUID.randomUUID(),"é".repeat(499)+"🎟").status());
        assertThrows(ApiException.class,()->replay.replay(admin,e,UUID.randomUUID(),"🎟".repeat(501)));
    }
}
