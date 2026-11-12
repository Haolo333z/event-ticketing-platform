package com.haolo.ticketing;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import com.haolo.ticketing.bookings.*;
import com.haolo.ticketing.payments.*;
import com.haolo.ticketing.expiry.ExpiryService;
import com.haolo.ticketing.identity.*;
import com.haolo.ticketing.messaging.*;
import com.haolo.ticketing.operations.*;
import com.haolo.ticketing.shared.ApiException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=${KAFKA_IT_PG_URL:${PG_IT_URL}}","expiry.enabled=false","messaging.enabled=true","messaging.scheduling-enabled=false",
    "messaging.bootstrap-servers=${KAFKA_IT_BOOTSTRAP}","messaging.topic=${KAFKA_IT_TOPIC}","messaging.dlq-topic=${KAFKA_IT_TOPIC}.dlq",
    "logging.level.org.springframework.jdbc=INFO","logging.level.org.apache.kafka=WARN","messaging.group-prefix=${KAFKA_IT_TOPIC}","messaging.refund-simulated-failures=1"})
@ActiveProfiles("test") @DirtiesContext
@EnabledIfEnvironmentVariable(named="KAFKA_IT_BOOTSTRAP",matches=".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaRecoveryTest {
    @Autowired JdbcTemplate jdbc;@Autowired BookingCommandService bookings;@Autowired PaymentSimulatorService simulator;
    @Autowired PaymentResultProcessor processor;@Autowired BookingCancelService cancel;@Autowired ExpiryService expiry;
    @Autowired OutboxPublisher publisher;@Autowired EventConsumer consumer;@Autowired EventCodec codec;
    @Autowired RefundRecoveryService refunds;@Autowired ReplayService replay;@Autowired KafkaTemplate<String,String> kafka;
    @Autowired BookingTransactions transactions;@Autowired DemoSettings settings;
    @Autowired KafkaListenerEndpointRegistry registry;@Autowired ObjectMapper json;
    @Value("${messaging.topic}")String topic;@LocalServerPort int port;
    final DemoPrincipal owner=new DemoPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"),"CUSTOMER");
    final DemoPrincipal admin=new DemoPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000002"),"ADMIN");
    final HttpClient http=HttpClient.newHttpClient();
    UUID event,type;static final List<Map<String,Object>> evidence=new ArrayList<>();
    @BeforeEach void fixture(){
        event=UUID.randomUUID();type=UUID.randomUUID();
        jdbc.update("INSERT INTO ticketing.events(id,title,description,venue,sale_start_at,sale_end_at,starts_at,status) VALUES (?,'Kafka test','','Local',clock_timestamp()-interval '1h',clock_timestamp()+interval '1h',clock_timestamp()+interval '2h','PUBLISHED')",event);
        jdbc.update("INSERT INTO ticketing.ticket_types(id,event_id,name,mode,price_vnd) VALUES (?,?,'GA','GA',100000)",type,event);
        jdbc.update("INSERT INTO ticketing.inventory_ga(ticket_type_id,capacity) VALUES (?,10)",type);
    }
    UUID booking(int n){return bookings.create(owner,"kafka-"+UUID.randomUUID(),new GaBookingRequest(event,type,n),UUID.randomUUID()).bookingId();}
    PaymentSimulatorService.Receipt record(UUID b,String scenario){return simulator.record(owner,b,new SimulationInput(scenario,UUID.randomUUID()),UUID.randomUUID());}
    int count(String sql,Object... args){return jdbc.queryForObject(sql,Integer.class,args);}
    String state(UUID b){return jdbc.queryForObject("SELECT status FROM ticketing.bookings WHERE id=?",String.class,b);}
    UUID eventId(UUID b,String type){return jdbc.queryForObject("SELECT id FROM ticketing.outbox_events WHERE aggregate_id=? AND type=? ORDER BY aggregate_version DESC LIMIT 1",UUID.class,b,type);}
    void await(BooleanSupplier check){long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(System.nanoTime()<end){if(check.getAsBoolean())return;try{Thread.sleep(100);}catch(InterruptedException ex){throw new AssertionError(ex);}}assertTrue(check.getAsBoolean(),"Condition timed out");}
    void flush(UUID b){await(()->{publisher.publishOnce();return count("SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id=? AND status<>'PUBLISHED'",b)==0;});}
    void send(OutboxWriter.Event e){try{kafka.send(topic,e.aggregateId().toString(),codec.encode(e)).get(8,TimeUnit.SECONDS);}catch(Exception ex){throw new AssertionError(ex);}}
    int tickets(UUID b){return count("SELECT count(*) FROM ticketing.tickets WHERE booking_id=?",b);}
    int inbox(String group,UUID e){return count("SELECT count(*) FROM ticketing.inbox_events WHERE consumer=? AND event_id=?",group,e);}
    void due(UUID b){jdbc.update("UPDATE ticketing.bookings SET created_at=clock_timestamp()-interval '10s',expires_at=clock_timestamp()-interval '2s' WHERE id=?",b);}
    void available(){jdbc.update("UPDATE ticketing.outbox_events SET next_attempt_at=clock_timestamp() WHERE status='PENDING'");}
    @AfterAll static void save() throws Exception {Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target/part7-kafka-evidence.json"),new ObjectMapper().writeValueAsString(evidence));}

    @Test @Order(1) void schemaV1StrictAndInboxUniquePerConsumer(){
        UUID b=booking(1);var e=publisher.event(eventId(b,"BookingHeld"));String raw=codec.encode(e);
        assertEquals(e,codec.decode(raw,b.toString()));
        for(String invalid:List.of(raw.replace("\"schemaVersion\":1","\"schemaVersion\":2"),raw.replace("\"aggregateVersion\":1","\"aggregateVersion\":0"),raw.substring(0,raw.length()-1)+",\"extra\":true}",raw.replace("BookingHeld","Unknown")))assertThrows(RuntimeException.class,()->codec.decode(invalid,b.toString()));
        assertThrows(RuntimeException.class,()->codec.decode(raw,UUID.randomUUID().toString()));
        consumer.accept(EventConsumer.AUDIT,b.toString(),raw);consumer.accept(EventConsumer.AUDIT,b.toString(),raw);
        assertEquals(1,inbox(EventConsumer.AUDIT,e.eventId()));
        assertThrows(RuntimeException.class,()->jdbc.update("INSERT INTO ticketing.inbox_events SELECT * FROM ticketing.inbox_events WHERE event_id=?",e.eventId()));
        assertThrows(RuntimeException.class,()->consumer.accept(EventConsumer.AUDIT,b.toString(),raw.replace(e.correlationId().toString(),UUID.randomUUID().toString())));
        evidence.add(Map.of("case","T6.1 schema/inbox","result","PASS"));
    }
    @Test @Order(2) void asynchronousPaymentVersionTwoDuplicateAndConsumerRestart(){
        UUID b=booking(3);simulator.simulate(owner,b,new SimulationInput("SUCCESS",UUID.randomUUID()),UUID.randomUUID());
        assertEquals("PENDING_PAYMENT",state(b));assertEquals(0,tickets(b));
        UUID paymentEvent=eventId(b,"PaymentResultRecorded");assertEquals(2,publisher.event(paymentEvent).aggregateVersion());
        flush(b);await(()->state(b).equals("CONFIRMED"));flush(b);await(()->inbox(EventConsumer.PAYMENT,paymentEvent)==1);
        var e=publisher.event(paymentEvent);for(int i=0;i<10;i++)send(e);
        registry.getListenerContainers().forEach(c->c.stop());registry.getListenerContainers().forEach(c->c.start());send(e);
        UUID confirmed=eventId(b,"BookingConfirmed");for(int i=0;i<5;i++)send(publisher.event(confirmed));
        await(()->inbox(EventConsumer.AUDIT,confirmed)==1);
        assertEquals(3,tickets(b));assertEquals(1,inbox(EventConsumer.PAYMENT,paymentEvent));
        assertEquals(1,count("SELECT count(*) FROM ticketing.booking_status_history WHERE booking_id=? AND to_status='CONFIRMED'",b));
        assertEquals(3,count("SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
        evidence.add(Map.of("case","Kafka payment v2, 10 duplicates, consumer restart, 5 confirmed replays","tickets",tickets(b),"confirmTransitions",1));
    }
    @Test @Order(3) void brokerOffOnRetainsOutboxAndOrdersVersions() throws Exception {
        String container=System.getenv("KAFKA_IT_CONTAINER");assertTrue(container!=null&&container.startsWith("ticketing-kafka-it-"));
        UUID b=booking(2);record(b,"SUCCESS");
        docker("stop",container);
        try{
            publisher.publishOnce();assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM ticketing.outbox_events WHERE id=?",String.class,eventId(b,"BookingHeld")));
            assertEquals(1,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",eventId(b,"BookingHeld")));
            assertEquals(0,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",eventId(b,"PaymentResultRecorded")));
            assertEquals("PENDING_PAYMENT",state(b));assertEquals(0,tickets(b));
        }finally{docker("start",container);}
        waitBroker(container);available();flush(b);await(()->state(b).equals("CONFIRMED"));flush(b);
        await(()->count("SELECT count(*) FROM ticketing.projection_events WHERE aggregate_id=?",b)==3);
        assertEquals(List.of(1L,2L,3L),jdbc.queryForList("SELECT aggregate_version FROM ticketing.projection_events WHERE aggregate_id=? ORDER BY applied_at,aggregate_version",Long.class,b));
        assertEquals(2,tickets(b));evidence.add(Map.of("case","real broker stop/start","versions",List.of(1,2,3),"tickets",2,"retained",true));
    }
    static void docker(String command,String container) throws Exception {var p=new ProcessBuilder("docker",command,container).redirectErrorStream(true).start();assertTrue(p.waitFor(40,TimeUnit.SECONDS));assertEquals(0,p.exitValue(),new String(p.getInputStream().readAllBytes()));}
    static void waitBroker(String container) throws Exception {for(int i=0;i<40;i++){var p=new ProcessBuilder("docker","exec",container,"/opt/kafka/bin/kafka-topics.sh","--bootstrap-server","kafka:29092","--list").redirectErrorStream(true).start();p.getInputStream().readAllBytes();if(p.waitFor()==0)return;Thread.sleep(500);}fail("Kafka did not recover");}
    @Test @Order(4) void acknowledgedSendThenDatabaseFailureResendsSameId(){
        UUID b=booking(1);UUID id=eventId(b,"BookingHeld");
        jdbc.execute("CREATE FUNCTION ticketing.test_ack_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+id+"'::uuid AND NEW.status='PUBLISHED' THEN RAISE EXCEPTION 'test ack DB crash'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_ack_failure BEFORE UPDATE ON ticketing.outbox_events FOR EACH ROW EXECUTE FUNCTION ticketing.test_ack_failure()");
        try{assertThrows(RuntimeException.class,()->publisher.publishOnce());await(()->inbox(EventConsumer.AUDIT,id)==1);assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM ticketing.outbox_events WHERE id=?",String.class,id));}
        finally{jdbc.execute("DROP TRIGGER test_ack_failure ON ticketing.outbox_events");jdbc.execute("DROP FUNCTION ticketing.test_ack_failure()");}
        flush(b);assertEquals(1,inbox(EventConsumer.AUDIT,id));assertEquals(1,count("SELECT count(*) FROM ticketing.projection_events WHERE aggregate_id=?",b));
    }
    @Test @Order(5) void paymentTransactionFailureRollsBackInboxTicketsAndRetriesViaKafka(){
        UUID b=booking(2);record(b,"SUCCESS");UUID id=eventId(b,"PaymentResultRecorded");
        jdbc.execute("CREATE SEQUENCE ticketing.test_delivery_attempts");
        jdbc.execute("CREATE FUNCTION ticketing.test_ticket_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"+b+"'::uuid THEN PERFORM nextval('ticketing.test_delivery_attempts'); RAISE EXCEPTION 'test ticket failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_ticket_failure BEFORE INSERT ON ticketing.tickets FOR EACH ROW EXECUTE FUNCTION ticketing.test_ticket_failure()");
        try{
            flush(b);await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE consumer=? AND event_id=?",EventConsumer.PAYMENT,id)==1);
            assertEquals("PENDING_PAYMENT",state(b));assertEquals(0,tickets(b));assertEquals(0,inbox(EventConsumer.PAYMENT,id));assertEquals(2,count("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
            assertEquals(0,count("SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id=? AND type='BookingConfirmed'",b));
            assertEquals(3,count("SELECT attempts FROM ticketing.message_failures WHERE consumer=? AND event_id=?",EventConsumer.PAYMENT,id));
            assertEquals(3,count("SELECT last_value FROM ticketing.test_delivery_attempts"));
        }finally{jdbc.execute("DROP TRIGGER test_ticket_failure ON ticketing.tickets");jdbc.execute("DROP FUNCTION ticketing.test_ticket_failure()");jdbc.execute("DROP SEQUENCE ticketing.test_delivery_attempts");}
        replay.replay(admin,id,UUID.randomUUID(),"Remove injected database failure");flush(b);await(()->state(b).equals("CONFIRMED"));
        await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE consumer=? AND event_id=? AND status='RESOLVED'",EventConsumer.PAYMENT,id)==1);
        assertEquals(2,tickets(b));assertEquals(1,inbox(EventConsumer.PAYMENT,id));evidence.add(Map.of("case","consumer rollback + 3 deliveries + admin replay","tickets",2,"result","PASS"));
    }
    @Test @Order(6) void realGapIsParkedThenOrderedReplayRecoversWithoutIssuingTickets(){
        UUID b=booking(2);var result=record(b,"SUCCESS");processor.process(b,result.paymentId());
        UUID confirmed=eventId(b,"BookingConfirmed");send(publisher.event(confirmed));
        await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE consumer=? AND event_id=?",EventConsumer.AUDIT,confirmed)==1);
        assertEquals(0,inbox(EventConsumer.AUDIT,confirmed));assertEquals(2,tickets(b));
        flush(b);await(()->inbox(EventConsumer.AUDIT,confirmed)==1);
        await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE event_id=? AND status='RESOLVED'",confirmed)==1);
        replay.replay(admin,confirmed,UUID.randomUUID(),"Replay confirmed notification");flush(b);assertEquals(2,tickets(b));
        assertEquals(3,count("SELECT last_applied_version FROM ticketing.event_projections WHERE aggregate_id=?",b));
        evidence.add(Map.of("case","version 3 before 1/2, gap parked and replayed","ticketsBefore",2,"ticketsAfter",2));
    }
    @Test @Order(7) void malformedEventRetriesThenDurableRecoveryAndKafkaDlq() throws Exception {
        String key=UUID.randomUUID().toString();kafka.send(topic,key,"{\"schemaVersion\":999}").get(8,TimeUnit.SECONDS);
        await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=?",key)==2);
        publisher.publishOnce();await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=? AND dlq_published_at IS NOT NULL",key)==2);
        assertEquals(2,count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=? AND attempts=3 AND raw_event=? AND event_id IS NULL",key,"{\"schemaVersion\":999}"));
        // Read the actual DLQ, not just its database delivery marker.
        try(var dlq=new org.apache.kafka.clients.consumer.KafkaConsumer<String,String>(Map.of("bootstrap.servers",System.getenv("KAFKA_IT_BOOTSTRAP"),"group.id","dlq-proof-"+UUID.randomUUID(),"auto.offset.reset","earliest","key.deserializer","org.apache.kafka.common.serialization.StringDeserializer","value.deserializer","org.apache.kafka.common.serialization.StringDeserializer"))){
            dlq.subscribe(List.of(topic+".dlq"));Set<String> found=new HashSet<>();await(()->{for(var r:dlq.poll(java.time.Duration.ofMillis(300)))if(r.value().contains(key))found.add(r.key());return found.size()==2;});
        }
    }
    @Test @Order(8) void replayRoleProfileAuditValidationAndBoundedBudget() throws Exception {
        UUID b=booking(1),id=eventId(b,"BookingHeld"),request=UUID.randomUUID();String path="/api/admin/operations/events/"+id+"/replay";
        String body=json.writeValueAsString(Map.of("requestId",request,"reason","Broker restored"));
        assertEquals(401,http("POST",path,body,null).statusCode());assertEquals(403,http("POST",path,body,"demo-customer-token").statusCode());
        assertEquals(202,http("POST",path,body,"demo-admin-token").statusCode());assertEquals(202,http("POST",path,body,"demo-admin-token").statusCode());
        assertEquals(409,http("POST",path,body.replace("Broker restored","Different reason"),"demo-admin-token").statusCode());
        assertEquals(400,http("POST",path,"{}","demo-admin-token").statusCode());
        assertEquals(413,http("POST",path," ".repeat(8193),"demo-admin-token").statusCode());
        assertEquals(202,http("POST",path,body.replace(request.toString(),UUID.randomUUID().toString()),"demo-admin-token").statusCode());
        assertEquals(202,http("POST",path,body.replace(request.toString(),UUID.randomUUID().toString()),"demo-admin-token").statusCode());
        var limit=http("POST",path,body.replace(request.toString(),UUID.randomUUID().toString()),"demo-admin-token");assertEquals(409,limit.statusCode());assertTrue(limit.body().contains("REPLAY_LIMIT_REACHED"));
        assertEquals(3,count("SELECT jsonb_array_length(replay_requests) FROM ticketing.outbox_events WHERE id=?",id));
        assertTrue(jdbc.queryForObject("SELECT replay_requests::text FROM ticketing.outbox_events WHERE id=?",String.class,id).contains(admin.accountId().toString()));
        assertEquals(200,http("GET","/api/admin/operations/outbox?page=0&size=10",null,"demo-admin-token").statusCode());assertEquals(400,http("GET","/api/admin/operations/outbox?size=101",null,"demo-admin-token").statusCode());
        var prod=new org.springframework.mock.env.MockEnvironment();prod.setActiveProfiles("prod");
        var forbidden=new ReplayService(jdbc,new BookingTransactions(null,null,jdbc),json,new DemoSettings(prod,false,"",""));
        assertEquals(403,assertThrows(ApiException.class,()->forbidden.replay(admin,id,request,"reason")).status);
    }
    HttpResponse<String> http(String method,String path,String body,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)builder.header("Authorization","Bearer "+token);
        if(body!=null)builder.header("Content-Type","application/json");
        return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test @Order(9) void timelyDurablePaymentWinsExpiryEvenWhileKafkaHasNotDelivered(){
        UUID b=booking(2);var result=record(b,"SUCCESS");
        // Move deadline after the result but before now, preserving a timely durable ledger.
        jdbc.update("UPDATE ticketing.bookings SET expires_at=(SELECT recorded_at+interval '1ms' FROM ticketing.payments WHERE id=?) WHERE id=?",result.paymentId(),b);
        await(()->jdbc.queryForObject("SELECT expires_at<clock_timestamp() FROM ticketing.bookings WHERE id=?",Boolean.class,b));
        expiry.runOnce();assertEquals("CONFIRMED",state(b));flush(b);await(()->inbox(EventConsumer.PAYMENT,eventId(b,"PaymentResultRecorded"))==1);
        assertEquals(2,tickets(b));assertEquals(2,count("SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
    }
    @Test @Order(10) void expiredLateSuccessRefundRetryHistoryAndConcurrentDuplicateAreIdempotent() throws Exception {
        UUID b=booking(2);due(b);expiry.runOnce();assertEquals("EXPIRED",state(b));var result=record(b,"SUCCESS");flush(b);await(()->state(b).equals("REFUND_REQUIRED"));
        UUID comp=jdbc.queryForObject("SELECT id FROM ticketing.payment_compensations WHERE payment_id=?",UUID.class,result.paymentId());
        assertFalse(refunds.attempt(comp));assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM ticketing.payment_compensations WHERE id=?",String.class,comp));
        jdbc.update("UPDATE ticketing.payment_compensations SET next_attempt_at=clock_timestamp() WHERE id=?",comp);
        try(var pool=Executors.newFixedThreadPool(6)){var jobs=new ArrayList<Callable<Boolean>>();for(int i=0;i<12;i++)jobs.add(()->refunds.attempt(comp));int success=0;for(var f:pool.invokeAll(jobs))if(f.get())success++;assertEquals(1,success);}
        for(int i=0;i<5;i++)send(publisher.event(eventId(b,"PaymentResultRecorded")));refunds.runOnce();
        assertEquals(1,count("SELECT count(*) FROM ticketing.payment_compensations WHERE payment_id=?",result.paymentId()));
        assertEquals(2,count("SELECT count(*) FROM ticketing.compensation_attempts WHERE compensation_id=?",comp));
        assertEquals(1,count("SELECT count(*) FROM ticketing.compensation_attempts WHERE compensation_id=? AND outcome='COMPLETED'",comp));
        assertEquals("REFUND_REQUIRED",state(b));assertEquals(0,tickets(b));assertEquals(0,count("SELECT held_count+sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
        evidence.add(Map.of("case","COMP-01: late payment, failure/retry, 12 concurrent refund calls","obligations",1,"attempts",2,"completions",1,"tickets",0,"heldPlusSold",0));
    }
    @Test @Order(11) void cancelledLateSuccessWithoutRefundEventStillRecoveredAndHistoryRollback(){
        UUID b=booking(1);cancel.cancel(owner,b,UUID.randomUUID());var r=record(b,"SUCCESS");processor.process(b,r.paymentId());
        assertEquals(0,count("SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id=? AND type='BookingRefundRequired'",b));
        UUID comp=jdbc.queryForObject("SELECT id FROM ticketing.payment_compensations WHERE payment_id=?",UUID.class,r.paymentId());
        jdbc.execute("CREATE FUNCTION ticketing.test_refund_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+comp+"'::uuid THEN RAISE EXCEPTION 'test refund rollback'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_refund_failure BEFORE UPDATE ON ticketing.payment_compensations FOR EACH ROW EXECUTE FUNCTION ticketing.test_refund_failure()");
        try{assertThrows(ApiException.class,()->refunds.attempt(comp));assertEquals(0,count("SELECT count(*) FROM ticketing.compensation_attempts WHERE compensation_id=?",comp));assertEquals(0,count("SELECT attempt_count FROM ticketing.payment_compensations WHERE id=?",comp));}
        finally{jdbc.execute("DROP TRIGGER test_refund_failure ON ticketing.payment_compensations");jdbc.execute("DROP FUNCTION ticketing.test_refund_failure()");}
        refunds.runOnce();jdbc.update("UPDATE ticketing.payment_compensations SET next_attempt_at=clock_timestamp() WHERE id=?",comp);refunds.runOnce();
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM ticketing.payment_compensations WHERE id=?",String.class,comp));assertEquals("CANCELLED",state(b));assertEquals(0,tickets(b));
    }
    @Test @Order(12) void exhaustedPublisherBudgetRetainsEventAndBlocksLaterVersions() throws Exception {
        UUID b=booking(1);record(b,"FAILURE");String container=System.getenv("KAFKA_IT_CONTAINER");assertTrue(container.startsWith("ticketing-kafka-it-"));docker("stop",container);
        try{for(int i=0;i<3;i++){available();publisher.publishOnce();}
            assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM ticketing.outbox_events WHERE id=?",String.class,eventId(b,"BookingHeld")));
            assertEquals(3,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",eventId(b,"BookingHeld")));
            assertEquals(0,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",eventId(b,"PaymentResultRecorded")));
            assertEquals(2,count("SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id=?",b));
        }finally{docker("start",container);}waitBroker(container);
        replay.replay(admin,eventId(b,"BookingHeld"),UUID.randomUUID(),"Broker recovered after bounded retry");flush(b);await(()->state(b).equals("PAYMENT_FAILED"));assertEquals(0,tickets(b));
    }
    @Test @Order(13) void exhaustedRefundBudgetParksSameObligationWithThreeHistoryRows(){
        UUID b=booking(1);cancel.cancel(owner,b,UUID.randomUUID());var r=record(b,"SUCCESS");processor.process(b,r.paymentId());
        UUID comp=jdbc.queryForObject("SELECT id FROM ticketing.payment_compensations WHERE payment_id=?",UUID.class,r.paymentId());
        var failing=new RefundRecoveryService(jdbc,transactions,settings,3);
        for(int i=0;i<3;i++){jdbc.update("UPDATE ticketing.payment_compensations SET next_attempt_at=clock_timestamp() WHERE id=?",comp);assertFalse(failing.attempt(comp));}
        assertFalse(refunds.attempt(comp));assertEquals(3,count("SELECT attempt_count FROM ticketing.payment_compensations WHERE id=?",comp));
        assertEquals(3,count("SELECT count(*) FROM ticketing.compensation_attempts WHERE compensation_id=? AND outcome='FAILED'",comp));
        assertEquals(1,count("SELECT count(*) FROM ticketing.payment_compensations WHERE payment_id=?",r.paymentId()));
        evidence.add(Map.of("case","bounded refund failure","attempts",3,"obligations",1,"result","FAILED retained for investigation"));
    }
    @Test @Order(14) void duplicateFailureAndExpiredNotificationsNeverReleaseAnotherHold(){
        UUID b=booking(2);record(b,"FAILURE");flush(b);await(()->state(b).equals("PAYMENT_FAILED"));flush(b);
        UUID another=booking(3);var result=publisher.event(eventId(b,"PaymentResultRecorded"));var failed=publisher.event(eventId(b,"BookingPaymentFailed"));
        for(int i=0;i<10;i++){send(result);send(failed);}await(()->inbox(EventConsumer.AUDIT,failed.eventId())==1);
        assertEquals(3,count("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));assertEquals("PENDING_PAYMENT",state(another));assertEquals(0,tickets(b));
        UUID due=booking(1);due(due);expiry.runOnce();flush(due);var expired=publisher.event(eventId(due,"BookingExpired"));
        for(int i=0;i<10;i++)send(expired);await(()->inbox(EventConsumer.AUDIT,expired.eventId())==1);
        assertEquals(3,count("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
    }
    @Test @Order(15) void publisherSessionLeaderPreventsSecondPublisherFromSending() throws Exception {
        UUID b=booking(1);UUID id=eventId(b,"BookingHeld");
        try(var connection=java.sql.DriverManager.getConnection(System.getenv("KAFKA_IT_PG_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"));var s=connection.createStatement()){
            s.execute("SELECT pg_advisory_lock(7469636)");try{assertEquals(0,publisher.publishOnce());assertEquals(0,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",id));}finally{s.execute("SELECT pg_advisory_unlock(7469636)");}
        }
        flush(b);await(()->inbox(EventConsumer.AUDIT,id)==1);
    }
}
