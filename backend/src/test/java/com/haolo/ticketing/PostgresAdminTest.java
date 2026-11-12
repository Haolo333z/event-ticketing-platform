package com.haolo.ticketing;

import java.io.ByteArrayInputStream;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.haolo.ticketing.identity.DemoSettings;
import static org.junit.jupiter.api.Assertions.*;

class PostgresAdminTest extends PostgresCatalogTest {
    @Autowired JdbcTemplate jdbc;
    static final String GA="30000000-0000-4000-8000-000000000002", EVENT="20000000-0000-4000-8000-000000000002";
    static final String ADMIN="demo-admin-token", CUSTOMER="demo-customer-token";
    HttpResponse<String> send(String method,String path,String body,String token,String... headers) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        for(int i=0;i<headers.length;i+=2)b.header(headers[i],headers[i+1]);
        return client.send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String createBody(){return """
      {"title":"Admin draft","description":"Test","venue":"Local","saleStartAt":"2026-01-01T00:00:00Z","saleEndAt":"2099-01-01T00:00:00Z","startsAt":"2099-01-02T00:00:00Z","status":"DRAFT","ticketTypes":[{"name":"GA","mode":"GA","priceVnd":123000,"capacity":20}]}
      """;}
    @Test void authAndStartupGuard() throws Exception {
        assertEquals(401,send("POST","/api/admin/events",createBody(),null).statusCode());
        assertEquals(401,send("POST","/api/admin/events",createBody(),"unknown").statusCode());
        assertEquals(401,send("GET","/api/events","","unknown").statusCode());
        assertEquals(403,send("POST","/api/admin/events",createBody(),CUSTOMER,"X-Role","ADMIN","X-User-Id","10000000-0000-4000-8000-000000000002").statusCode());
        new ApplicationContextRunner().withInitializer(c->c.getEnvironment().setActiveProfiles("prod"))
          .withUserConfiguration(DemoSettings.class).withPropertyValues("demo.auth.enabled=true","demo.auth.customer-token=fixture-a","demo.auth.admin-token=fixture-b")
          .run(c->assertNotNull(c.getStartupFailure()));
        new ApplicationContextRunner().withInitializer(c->c.getEnvironment().setActiveProfiles("prod"))
          .withUserConfiguration(DemoSettings.class).run(c->assertFalse(c.getBean(DemoSettings.class).enabled));
    }
    @Test void adminCreatesAndValidatesCatalog() throws Exception {
        var created=send("POST","/api/admin/events",createBody(),ADMIN);assertEquals(201,created.statusCode(),created.body());
        var event=mapper.readTree(created.body());String id=event.get("id").asText();
        assertEquals(404,get("/api/events/"+id).statusCode());
        assertEquals(200,send("PATCH","/api/admin/events/"+id,"{\"title\":\"Changed\"}",ADMIN).statusCode());
        for(String body:List.of("{}",createBody().replace("123000","-1"),createBody().replace("123000","1.5"),createBody().replace("\"GA\",\"priceVnd\"","\"BAD\",\"priceVnd\""),createBody().replace("2099-01-01T00:00:00Z","2020-01-01T00:00:00Z"),createBody().replace("\"title\":","\"userId\":\"fake\",\"title\":")))
            assertEquals(400,send("POST","/api/admin/events",body,ADMIN).statusCode(),body);
        assertEquals(400,send("PATCH","/api/admin/events/"+id,"{\"ticketTypes\":[{\"id\":\""+event.get("ticketTypes").get(0).get("id").asText()+"\",\"mode\":\"SEATED\"}]}",ADMIN).statusCode());
        assertEquals(400,send("POST","/api/admin/events",createBody().replace("2026-01-01T00:00:00Z","invalidZ"),ADMIN).statusCode());
    }
    @Test void capacityPriceAndSeatsAreProtected() throws Exception {
        UUID booking=UUID.randomUUID(),item=UUID.randomUUID();
        jdbc.update("INSERT INTO ticketing.bookings(id,account_id,event_id,ticket_type_id,payment_intent_id,status,expires_at,total_amount_vnd,aggregate_version) VALUES (?,'10000000-0000-4000-8000-000000000001',?,?,?,'PENDING_PAYMENT',clock_timestamp()+interval '10m',100000,1)",booking,UUID.fromString(EVENT),UUID.fromString(GA),UUID.randomUUID());
        jdbc.update("INSERT INTO ticketing.booking_items(id,booking_id,event_id,ticket_type_id,quantity,unit_price_vnd,total_price_vnd) VALUES (?,?,?,?,1,100000,100000)",item,booking,UUID.fromString(EVENT),UUID.fromString(GA));
        jdbc.update("UPDATE ticketing.inventory_ga SET held_count=3,sold_count=2 WHERE ticket_type_id=?",UUID.fromString(GA));
        try {
            String path="/api/admin/events/"+EVENT;
            String bad="{\"title\":\"Must rollback\",\"ticketTypes\":[{\"id\":\""+GA+"\",\"capacity\":4}]}";
            var r=send("PATCH",path,bad,ADMIN);assertEquals(409,r.statusCode(),r.body());assertEquals("CATALOG_CONFLICT",mapper.readTree(r.body()).get("code").asText());
            assertNotEquals("Must rollback",jdbc.queryForObject("SELECT title FROM ticketing.events WHERE id=?",String.class,UUID.fromString(EVENT)));
            assertEquals(200,send("PATCH",path,"{\"ticketTypes\":[{\"id\":\""+GA+"\",\"capacity\":110,\"priceVnd\":150000}]}",ADMIN).statusCode());
            assertEquals(100000,jdbc.queryForObject("SELECT unit_price_vnd FROM ticketing.booking_items WHERE id=?",Long.class,item));
            assertEquals(150000,jdbc.queryForObject("SELECT price_vnd FROM ticketing.ticket_types WHERE id=?",Long.class,UUID.fromString(GA)));
            // A seat held by a real foundation booking cannot be edited or removed through v0.
            UUID seated=UUID.randomUUID();
            jdbc.update("INSERT INTO ticketing.bookings(id,account_id,event_id,ticket_type_id,payment_intent_id,status,expires_at,total_amount_vnd,aggregate_version) VALUES (?,'10000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001',?,'PENDING_PAYMENT',clock_timestamp()+interval '10m',200000,1)",seated,UUID.randomUUID());
            try {
                jdbc.update("UPDATE ticketing.seats SET status='HELD',held_by_booking_id=?,hold_expires_at=clock_timestamp()+interval '10m' WHERE label='A1' AND event_id='20000000-0000-4000-8000-000000000001'",seated);
                assertEquals(400,send("PATCH","/api/admin/events/20000000-0000-4000-8000-000000000001","{\"seats\":[]}",ADMIN).statusCode());
                assertEquals(405,send("DELETE","/api/admin/events/20000000-0000-4000-8000-000000000001","",ADMIN).statusCode());
                assertEquals("HELD",jdbc.queryForObject("SELECT status FROM ticketing.seats WHERE label='A1' AND event_id='20000000-0000-4000-8000-000000000001'",String.class));
            } finally {jdbc.update("UPDATE ticketing.seats SET status='AVAILABLE',held_by_booking_id=NULL,hold_expires_at=NULL WHERE held_by_booking_id=?",seated);jdbc.update("DELETE FROM ticketing.bookings WHERE id=?",seated);}
        } finally {
            jdbc.update("DELETE FROM ticketing.booking_items WHERE id=?",item);jdbc.update("DELETE FROM ticketing.bookings WHERE id=?",booking);
            jdbc.update("UPDATE ticketing.inventory_ga SET capacity=100,held_count=0,sold_count=0 WHERE ticket_type_id=?",UUID.fromString(GA));
            jdbc.update("UPDATE ticketing.ticket_types SET price_vnd=100000 WHERE id=?",UUID.fromString(GA));
        }
    }
    @Test void adminCreatesSeatedCatalogAndDuplicateLabelsRollback() throws Exception {
        String seated=createBody().replace("{\"name\":\"GA\",\"mode\":\"GA\",\"priceVnd\":123000,\"capacity\":20}",
            "{\"name\":\"Seat\",\"mode\":\"SEATED\",\"priceVnd\":200000,\"seats\":[{\"label\":\"A1\"},{\"label\":\"A2\"}]}");
        var response=send("POST","/api/admin/events",seated,ADMIN);assertEquals(201,response.statusCode(),response.body());
        var data=mapper.readTree(response.body());assertEquals("SEATED",data.get("ticketTypes").get(0).get("mode").asText());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE event_id=?",Integer.class,UUID.fromString(data.get("id").asText())));
        long before=jdbc.queryForObject("SELECT count(*) FROM ticketing.events",Long.class);
        assertEquals(400,send("POST","/api/admin/events",seated.replace("\"A2\"","\"A1\""),ADMIN).statusCode());
        assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM ticketing.events",Long.class));
    }
    @Test void corsBodyLimitsAndMediaTypes() throws Exception {
        var good=send("OPTIONS","/api/admin/events","",null,"Origin","http://localhost:5173","Access-Control-Request-Method","POST","Access-Control-Request-Headers","authorization,content-type");
        assertEquals(204,good.statusCode());assertEquals("http://localhost:5173",good.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(good.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
        var denied=send("OPTIONS","/api/admin/events","",null,"Origin","https://bad.example","Access-Control-Request-Method","POST");
        assertEquals(403,denied.statusCode());assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        assertEquals(413,send("POST","/api/admin/events"," ".repeat(131073),ADMIN).statusCode());
        var chunked=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/events")).header("Content-Type","application/json").header("Authorization","Bearer "+ADMIN)
          .POST(HttpRequest.BodyPublishers.ofInputStream(()->new ByteArrayInputStream(new byte[131073]))).build();
        assertEquals(413,client.send(chunked,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(415,send("POST","/api/admin/events",createBody(),ADMIN,"Content-Encoding","gzip").statusCode());
        assertEquals(400,send("GET","/api/events","{}",null).statusCode());
        assertEquals(400,send("POST","/api/admin/events","[".repeat(11)+"]".repeat(11),ADMIN).statusCode());
        var wrongMedia=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/events"))
            .header("Content-Type","text/plain").header("Authorization","Bearer "+ADMIN).POST(HttpRequest.BodyPublishers.ofString(createBody())).build();
        assertEquals(415,client.send(wrongMedia,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void capacityUpdateRechecksAfterContendedLock() throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("PG_IT_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"))){
            c.setAutoCommit(false);try(var s=c.createStatement()){s.executeQuery("SELECT * FROM ticketing.inventory_ga WHERE ticket_type_id='"+GA+"' FOR UPDATE");}
            var future=CompletableFuture.supplyAsync(()->{try{return send("PATCH","/api/admin/events/"+EVENT,"{\"ticketTypes\":[{\"id\":\""+GA+"\",\"capacity\":1}]}",ADMIN);}catch(Exception ex){throw new RuntimeException(ex);}});
            Thread.sleep(300);
            try(var s=c.createStatement()){s.executeUpdate("UPDATE ticketing.inventory_ga SET held_count=2 WHERE ticket_type_id='"+GA+"'");}c.commit();
            assertEquals(409,future.get(10,TimeUnit.SECONDS).statusCode());
        } finally {jdbc.update("UPDATE ticketing.inventory_ga SET held_count=0 WHERE ticket_type_id=?",UUID.fromString(GA));}
    }
    @Test void lockTimeoutRollsBackBefore503() throws Exception {
        String original=jdbc.queryForObject("SELECT title FROM ticketing.events WHERE id=?",String.class,UUID.fromString(EVENT));
        try(var c=DriverManager.getConnection(System.getenv("PG_IT_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"))){
            c.setAutoCommit(false);try(var s=c.createStatement()){s.executeQuery("SELECT * FROM ticketing.inventory_ga WHERE ticket_type_id='"+GA+"' FOR UPDATE");}
            var response=send("PATCH","/api/admin/events/"+EVENT,"{\"title\":\"Must not commit\",\"ticketTypes\":[{\"id\":\""+GA+"\",\"capacity\":99}]}",ADMIN);
            assertEquals(503,response.statusCode(),response.body());assertEquals("1",response.headers().firstValue("Retry-After").orElseThrow());
            assertEquals("BOOKING_BUSY",mapper.readTree(response.body()).get("code").asText());
            assertEquals(original,jdbc.queryForObject("SELECT title FROM ticketing.events WHERE id=?",String.class,UUID.fromString(EVENT)));
            c.rollback();
        }
    }
}
