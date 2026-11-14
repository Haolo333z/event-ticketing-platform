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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
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
@ActiveProfiles("test") @DirtiesContext @ExtendWith(OutputCaptureExtension.class)
@EnabledIfEnvironmentVariable(named="KAFKA_IT_BOOTSTRAP",matches=".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaRecoveryTest {
    @Autowired RecoveryQuery recovery;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    double retries(String stage,String consumer){return metrics.get("ticketing.messaging.retries").tags("stage",stage,"consumer",consumer).counter().count();}
    double dlqDeliveries(String outcome){return metrics.get("ticketing.messaging.dlq.deliveries").tag("outcome",outcome).counter().count();}
    double gauge(String name,String...tags){return metrics.get(name).tags(tags).gauge().value();}
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
    void snap(String scenario,String phase){RecoveryEvidence.capture(jdbc,scenario,phase,event);}
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

    String businessSnapshot(UUID booking){
        return jdbc.queryForObject("""
            SELECT md5(jsonb_build_object(
              'booking',(SELECT to_jsonb(b) FROM ticketing.bookings b WHERE id=?),
              'items',(SELECT jsonb_agg(to_jsonb(i) ORDER BY id) FROM ticketing.booking_items i WHERE booking_id=?),
              'tickets',(SELECT jsonb_agg(to_jsonb(t) ORDER BY id) FROM ticketing.tickets t WHERE booking_id=?),
              'history',(SELECT jsonb_agg(to_jsonb(h) ORDER BY id) FROM ticketing.booking_status_history h WHERE booking_id=?),
              'payments',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM ticketing.payments p WHERE booking_id=?),
              'inventory',(SELECT to_jsonb(g) FROM ticketing.inventory_ga g WHERE ticket_type_id=?),
              'events',(SELECT jsonb_agg(jsonb_build_object('id',id,'type',type,'version',aggregate_version,'payload',payload,'correlation',correlation_id,'created',created_at) ORDER BY id) FROM ticketing.outbox_events WHERE aggregate_id=?),
              'inbox',(SELECT jsonb_agg(to_jsonb(i) ORDER BY consumer,event_id) FROM ticketing.inbox_events i WHERE aggregate_id=?),
              'projection',(SELECT jsonb_agg(to_jsonb(p) ORDER BY aggregate_version) FROM ticketing.projection_events p WHERE aggregate_id=?)
            )::text)
            """,String.class,booking,booking,booking,booking,booking,type,booking,booking,booking);
    }
    @Test @Order(18) void recoveryHttpReplaysProcessedEventsWithoutAnySecondBusinessEffect(CapturedOutput output)throws Exception{
        UUID booking=booking(2);record(booking,"SUCCESS");flush(booking);await(()->state(booking).equals("CONFIRMED"));flush(booking);
        UUID payment=eventId(booking,"PaymentResultRecorded"),confirmed=eventId(booking,"BookingConfirmed");
        await(()->inbox(EventConsumer.AUDIT,confirmed)==1&&inbox(EventConsumer.PAYMENT,payment)==1);
        String before=businessSnapshot(booking),path="/api/admin/operations/events/"+payment;
        snap("replay-noop","before");
        var first=json.readTree(http("GET",path,null,"demo-admin-token").body());assertEquals(3,first.path("remainingReplays").intValue());assertEquals(2,first.path("consumers").size());assertEquals("PUBLISHED",first.path("status").asString());
        UUID request=UUID.randomUUID();String reason="Đối soát event đã xử lý";String body=json.writeValueAsString(Map.of("requestId",request,"reason",reason));
        for(int i=0;i<2;i++)assertEquals(202,http("POST",path+"/replay",body,"demo-admin-token").statusCode());
        assertEquals(1,count("SELECT jsonb_array_length(replay_requests) FROM ticketing.outbox_events WHERE id=?",payment));
        flush(booking);await(()->traceLines(output,payment).stream().filter(n->n.path("outcome").asString().equals("DUPLICATE")).map(n->n.path("consumer").asString()).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of(EventConsumer.AUDIT,EventConsumer.PAYMENT)));
        int attempts=count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",payment);
        assertEquals(202,http("POST",path+"/replay",body,"demo-admin-token").statusCode());assertEquals(attempts,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",payment));assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM ticketing.outbox_events WHERE id=?",String.class,payment));
        assertEquals(before,businessSnapshot(booking));
        for(int i=0;i<2;i++){
            String next=json.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"reason","Lượt đối soát "+i));assertEquals(202,http("POST",path+"/replay",next,"demo-admin-token").statusCode());flush(booking);
        }
        await(()->traceLines(output,payment).stream().filter(n->n.path("stage").asString().equals("consumer_processed")&&n.path("outcome").asString().equals("DUPLICATE")&&n.path("consumer").asString().equals(EventConsumer.PAYMENT)).count()>=3);
        assertEquals(409,http("POST",path+"/replay",json.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"reason","Không vượt budget")),"demo-admin-token").statusCode());
        assertEquals(202,http("POST",path+"/replay",body,"demo-admin-token").statusCode());assertEquals(409,http("POST",path+"/replay",body.replace(reason,"Nội dung khác"),"demo-admin-token").statusCode());
        assertEquals(400,http("POST",path+"/replay",body.substring(0,body.length()-1)+",\"force\":true}","demo-admin-token").statusCode());
        var detail=json.readTree(http("GET",path,null,"demo-admin-token").body());assertEquals(0,detail.path("remainingReplays").intValue());assertEquals(3,detail.path("audit").size());assertEquals(first.path("consumers"),detail.path("consumers"));
        assertEquals(admin.accountId().toString(),detail.path("audit").get(0).path("adminAccountId").asString());assertEquals(reason,detail.path("audit").get(0).path("reason").asString());assertEquals(request.toString(),detail.path("audit").get(0).path("requestId").asString());
        String notification=json.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"reason","Replay BookingConfirmed không phát vé"));
        assertEquals(202,http("POST","/api/admin/operations/events/"+confirmed+"/replay",notification,"demo-admin-token").statusCode());flush(booking);
        await(()->traceLines(output,confirmed).stream().anyMatch(n->n.path("outcome").asString().equals("DUPLICATE")&&n.path("consumer").asString().equals(EventConsumer.AUDIT)));
        String after=businessSnapshot(booking);assertEquals(before,after);assertEquals(2,tickets(booking));assertEquals(2,count("SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
        snap("replay-noop","after");
        assertEquals(3,count("SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id=?",booking));assertEquals(2,count("SELECT count(*) FROM ticketing.booking_status_history WHERE booking_id=?",booking));
        evidence.add(Map.of("case","T8.3 HTTP replay no-op via real Kafka, audit and budget","beforeBusinessHash",before,"afterBusinessHash",after,"tickets",2,"historyRows",2,"businessEvents",3,"paymentReplayAuditRows",3,"remainingReplays",0,"result","PASS"));
    }
    @Test @Order(19) void recoveryReadViewIsAuthorizedPaginatedAndNeverLeaksRawFailureData()throws Exception{
        UUID booking=booking(1),id=eventId(booking,"BookingHeld");String marker="private-recovery-"+UUID.randomUUID();
        jdbc.update("UPDATE ticketing.outbox_events SET status='FAILED',last_error=? WHERE id=?",marker,id);
        var own=new HashSet<String>();own.add("OUTBOX:"+id);var failureIds=new ArrayList<UUID>();
        for(int i=0;i<22;i++){
            UUID failure=UUID.randomUUID();failureIds.add(failure);own.add("CONSUMER:"+failure);
            jdbc.update("INSERT INTO ticketing.message_failures(id,consumer,topic,partition_id,record_offset,record_key,raw_event,event_id,attempts,error,created_at) VALUES (?,'payment-v1',?,0,?,?,?,?,3,?,'2026-11-01T00:00:00Z')",failure,marker,i,marker,marker,id,marker);
        }
        String path="/api/admin/operations/events/"+id;
        for(String endpoint:List.of(path,"/api/admin/operations/recovery")){
            assertEquals(401,http("GET",endpoint,null,null).statusCode());assertEquals(401,http("GET",endpoint,null,"unknown").statusCode());assertEquals(403,http("GET",endpoint,null,"demo-customer-token").statusCode());assertEquals(200,http("GET",endpoint,null,"demo-admin-token").statusCode());
        }
        assertEquals(404,http("GET","/api/admin/operations/events/"+UUID.randomUUID(),null,"demo-admin-token").statusCode());assertEquals(404,http("POST","/api/admin/operations/events/"+UUID.randomUUID()+"/replay",json.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"reason","missing")),"demo-admin-token").statusCode());
        for(String query:List.of("?status=bad","?status=","?size=101","?page=-1","?page=10001"))assertEquals(400,http("GET","/api/admin/operations/recovery"+query,null,"demo-admin-token").statusCode());
        var seen=new LinkedHashSet<String>();var ownOrder=new ArrayList<String>();int total=-1;
        for(int page=0;;page++){
            var response=http("GET","/api/admin/operations/recovery?status=ALL&size=1&page="+page,null,"demo-admin-token");assertEquals(200,response.statusCode());assertFalse(response.body().contains(marker));assertFalse(response.body().contains("raw_event"));assertFalse(response.body().contains("payload"));
            var body=json.readTree(response.body());if(total<0)total=body.path("totalElements").intValue();assertEquals(total,body.path("totalElements").intValue());
            if(page==total){assertTrue(body.path("items").isEmpty());break;}
            var row=body.path("items").get(0);String key=row.path("source").asString()+":"+row.path("id").asString();assertTrue(seen.add(key));
            if(own.contains(key)){ownOrder.add(key);assertTrue(row.path("replayable").booleanValue());}
            if(row.path("eventId").isNull())assertFalse(row.path("replayable").booleanValue());
        }
        assertEquals(total,seen.size());assertTrue(seen.containsAll(own));
        assertEquals(failureIds.stream().map(UUID::toString).sorted(Comparator.reverseOrder()).toList(),ownOrder.stream().filter(k->k.startsWith("CONSUMER:")).map(k->k.substring("CONSUMER:".length())).toList());
        var response=http("GET",path,null,"demo-admin-token");assertFalse(response.body().contains(marker));var detail=json.readTree(response.body());assertEquals(22,detail.path("failures").size());assertEquals(3,detail.path("remainingReplays").intValue());assertTrue(detail.path("audit").isEmpty());
        var prod=new org.springframework.mock.env.MockEnvironment();prod.setActiveProfiles("prod");var denied=new RecoveryQuery(jdbc,new ReplayService(jdbc,transactions,json,new DemoSettings(prod,false,"","")),json);
        assertEquals(403,assertThrows(ApiException.class,()->denied.list(admin,0,20,"ALL")).status);assertEquals(403,assertThrows(ApiException.class,()->denied.event(admin,id)).status);
        evidence.add(Map.of("case","T8.3 recovery list/details authorization, pagination, safe diagnostics","ownRows",own.size(),"totalRowsObserved",total,"pageSize",1,"duplicates",0,"result","PASS"));
    }

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
        snap("consumer-restart-duplicates","before");
        var e=publisher.event(paymentEvent);for(int i=0;i<10;i++)send(e);
        registry.getListenerContainers().forEach(c->c.stop());registry.getListenerContainers().forEach(c->c.start());send(e);
        UUID confirmed=eventId(b,"BookingConfirmed");for(int i=0;i<5;i++)send(publisher.event(confirmed));
        await(()->inbox(EventConsumer.AUDIT,confirmed)==1);
        assertEquals(3,tickets(b));assertEquals(1,inbox(EventConsumer.PAYMENT,paymentEvent));
        assertEquals(1,count("SELECT count(*) FROM ticketing.booking_status_history WHERE booking_id=? AND to_status='CONFIRMED'",b));
        assertEquals(3,count("SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
        snap("consumer-restart-duplicates","after");
        evidence.add(Map.of("case","Kafka payment v2, 10 duplicates, consumer restart, 5 confirmed replays","tickets",tickets(b),"confirmTransitions",1));
    }
    @Test @Order(3) void brokerOffOnRetainsOutboxAndOrdersVersions(CapturedOutput output) throws Exception {
        String container=System.getenv("KAFKA_IT_CONTAINER");assertTrue(container!=null&&container.startsWith("ticketing-kafka-it-"));
        UUID b=booking(2);record(b,"SUCCESS");
        snap("broker-outage","before");
        double retriesBefore=retries("outbox","none");
        health("",200,"UP");health("/readiness",200,"UP");health("/liveness",200,"UP");
        docker("stop",container);
        try{
            health("",503,"DOWN");health("/readiness",503,"DOWN");health("/liveness",200,"UP");
            publisher.publishOnce();assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM ticketing.outbox_events WHERE id=?",String.class,eventId(b,"BookingHeld")));
            assertEquals(1,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",eventId(b,"BookingHeld")));
            assertEquals(0,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",eventId(b,"PaymentResultRecorded")));
            assertEquals("PENDING_PAYMENT",state(b));assertEquals(0,tickets(b));
            snap("broker-outage","offline");
            assertEquals(retriesBefore,retries("outbox","none"),"Initial failed send is not a retry");
            var failed=traceLines(output,eventId(b,"BookingHeld")).stream().filter(n->n.path("stage").asString().equals("outbox_publish_failed")).findFirst().orElseThrow();
            assertEquals(b.toString(),failed.path("bookingId").asString());assertEquals(publisher.event(eventId(b,"BookingHeld")).correlationId().toString(),failed.path("correlationId").asString());
        }finally{docker("start",container);}
        waitBroker(container);available();flush(b);await(()->state(b).equals("CONFIRMED"));flush(b);
        health("",200,"UP");health("/readiness",200,"UP");
        assertTrue(retries("outbox","none")>=retriesBefore+1);assertEquals(2,count("SELECT attempts FROM ticketing.outbox_events WHERE id=?",eventId(b,"BookingHeld")));
        await(()->count("SELECT count(*) FROM ticketing.projection_events WHERE aggregate_id=?",b)==3);
        assertEquals(List.of(1L,2L,3L),jdbc.queryForList("SELECT aggregate_version FROM ticketing.projection_events WHERE aggregate_id=? ORDER BY applied_at,aggregate_version",Long.class,b));
        assertEquals(2,tickets(b));evidence.add(Map.of("case","real broker stop/start","versions",List.of(1,2,3),"tickets",2,"retained",true));
        snap("broker-outage","recovered");
    }
    static void docker(String command,String container) throws Exception {var p=new ProcessBuilder("docker",command,container).redirectErrorStream(true).start();assertTrue(p.waitFor(40,TimeUnit.SECONDS));assertEquals(0,p.exitValue(),new String(p.getInputStream().readAllBytes()));}
    static void waitBroker(String container) throws Exception {for(int i=0;i<40;i++){var p=new ProcessBuilder("docker","exec",container,"/opt/kafka/bin/kafka-topics.sh","--bootstrap-server","kafka:29092","--list").redirectErrorStream(true).start();p.getInputStream().readAllBytes();if(p.waitFor()==0)return;Thread.sleep(500);}fail("Kafka did not recover");}
    @Test @Order(4) void acknowledgedSendThenDatabaseFailureResendsSameId(){
        UUID b=booking(1);UUID id=eventId(b,"BookingHeld");
        snap("ack-db-window","before");
        jdbc.execute("CREATE FUNCTION ticketing.test_ack_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+id+"'::uuid AND NEW.status='PUBLISHED' THEN RAISE EXCEPTION 'test ack DB crash'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_ack_failure BEFORE UPDATE ON ticketing.outbox_events FOR EACH ROW EXECUTE FUNCTION ticketing.test_ack_failure()");
        try{assertThrows(RuntimeException.class,()->publisher.publishOnce());await(()->inbox(EventConsumer.AUDIT,id)==1);assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM ticketing.outbox_events WHERE id=?",String.class,id));snap("ack-db-window","acked-db-rollback");}
        finally{jdbc.execute("DROP TRIGGER test_ack_failure ON ticketing.outbox_events");jdbc.execute("DROP FUNCTION ticketing.test_ack_failure()");}
        flush(b);assertEquals(1,inbox(EventConsumer.AUDIT,id));assertEquals(1,count("SELECT count(*) FROM ticketing.projection_events WHERE aggregate_id=?",b));
        snap("ack-db-window","resent");
    }
    @Test @Order(5) void paymentTransactionFailureRollsBackInboxTicketsAndRetriesViaKafka(CapturedOutput output){
        UUID b=booking(2);record(b,"SUCCESS");UUID id=eventId(b,"PaymentResultRecorded");
        snap("consumer-rollback-replay","before");
        double retriesBefore=retries("consumer",EventConsumer.PAYMENT);
        String marker="private-sql-"+UUID.randomUUID();
        jdbc.execute("CREATE SEQUENCE ticketing.test_delivery_attempts");
        jdbc.execute("CREATE FUNCTION ticketing.test_ticket_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"+b+"'::uuid THEN PERFORM nextval('ticketing.test_delivery_attempts'); RAISE EXCEPTION '"+marker+"'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_ticket_failure BEFORE INSERT ON ticketing.tickets FOR EACH ROW EXECUTE FUNCTION ticketing.test_ticket_failure()");
        try{
            flush(b);await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE consumer=? AND event_id=?",EventConsumer.PAYMENT,id)==1);
            assertEquals("PENDING_PAYMENT",state(b));assertEquals(0,tickets(b));assertEquals(0,inbox(EventConsumer.PAYMENT,id));assertEquals(2,count("SELECT held_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
            assertEquals(0,count("SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id=? AND type='BookingConfirmed'",b));
            assertEquals(3,count("SELECT attempts FROM ticketing.message_failures WHERE consumer=? AND event_id=?",EventConsumer.PAYMENT,id));
            assertEquals(3,count("SELECT last_value FROM ticketing.test_delivery_attempts"));
            snap("consumer-rollback-replay","dead-after-three-deliveries");
            assertEquals(retriesBefore+2,retries("consumer",EventConsumer.PAYMENT));
            await(()->gauge("ticketing.messaging.failures","consumer",EventConsumer.PAYMENT,"status","DEAD")==count("SELECT count(*) FROM ticketing.message_failures WHERE consumer=? AND status='DEAD'",EventConsumer.PAYMENT));
            var logs=traceLines(output,id).stream().filter(n->n.path("consumer").asString().equals(EventConsumer.PAYMENT)).toList();
            assertEquals(3,logs.stream().filter(n->n.path("stage").asString().equals("consumer_failed")).count());
            assertFalse(logs.stream().anyMatch(n->n.path("stage").asString().equals("consumer_processed")));
            for(var line:logs){assertEquals(b.toString(),line.path("bookingId").asString());assertEquals(publisher.event(id).correlationId().toString(),line.path("correlationId").asString());}
        }finally{jdbc.execute("DROP TRIGGER test_ticket_failure ON ticketing.tickets");jdbc.execute("DROP FUNCTION ticketing.test_ticket_failure()");jdbc.execute("DROP SEQUENCE ticketing.test_delivery_attempts");}
        replay.replay(admin,id,UUID.randomUUID(),"Remove injected database failure");flush(b);await(()->state(b).equals("CONFIRMED"));
        await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE consumer=? AND event_id=? AND status='RESOLVED'",EventConsumer.PAYMENT,id)==1);
        assertEquals(2,tickets(b));assertEquals(1,inbox(EventConsumer.PAYMENT,id));evidence.add(Map.of("case","consumer rollback + 3 deliveries + admin replay","tickets",2,"result","PASS"));
        snap("consumer-rollback-replay","recovered");
        assertFalse(output.getAll().contains(marker),"SQL error details must not reach logs");
        assertEquals(retriesBefore+2,retries("consumer",EventConsumer.PAYMENT),"Replay at a new offset is an initial delivery");
        await(()->gauge("ticketing.messaging.failures","consumer",EventConsumer.PAYMENT,"status","RESOLVED")==count("SELECT count(*) FROM ticketing.message_failures WHERE consumer=? AND status='RESOLVED'",EventConsumer.PAYMENT));
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
        double paymentRetries=retries("consumer",EventConsumer.PAYMENT),auditRetries=retries("consumer",EventConsumer.AUDIT),ackBefore=dlqDeliveries("ACKNOWLEDGED");
        int publishedBefore=count("SELECT count(*) FROM ticketing.message_failures WHERE dlq_published_at IS NOT NULL");
        snap("poison-dlq","before");
        String key=UUID.randomUUID().toString();kafka.send(topic,key,"{\"schemaVersion\":999}").get(8,TimeUnit.SECONDS);
        await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=?",key)==2);
        snap("poison-dlq","dead");
        assertEquals(paymentRetries+2,retries("consumer",EventConsumer.PAYMENT));assertEquals(auditRetries+2,retries("consumer",EventConsumer.AUDIT));
        await(()->gauge("ticketing.messaging.dlq.messages","state","PENDING")==count("SELECT count(*) FROM ticketing.message_failures WHERE dlq_published_at IS NULL AND dlq_attempts<3"));
        publisher.publishOnce();await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=? AND dlq_published_at IS NOT NULL",key)==2);
        assertEquals(count("SELECT count(*) FROM ticketing.message_failures WHERE dlq_published_at IS NOT NULL")-publishedBefore,dlqDeliveries("ACKNOWLEDGED")-ackBefore);
        assertEquals(2,count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=? AND attempts=3 AND raw_event=? AND event_id IS NULL",key,"{\"schemaVersion\":999}"));
        snap("poison-dlq","published");
        // Read the actual DLQ, not just its database delivery marker.
        try(var dlq=new org.apache.kafka.clients.consumer.KafkaConsumer<String,String>(Map.of("bootstrap.servers",System.getenv("KAFKA_IT_BOOTSTRAP"),"group.id","dlq-proof-"+UUID.randomUUID(),"auto.offset.reset","earliest","key.deserializer","org.apache.kafka.common.serialization.StringDeserializer","value.deserializer","org.apache.kafka.common.serialization.StringDeserializer"))){
            dlq.subscribe(List.of(topic+".dlq"));Set<String> found=new HashSet<>();await(()->{for(var r:dlq.poll(java.time.Duration.ofMillis(300)))if(r.value().contains(key))found.add(r.key());return found.size()==2;});
        }
    }
    @Test @Order(17) void realDlqOutageThenRetryUpdatesCountersAndDurableGauges()throws Exception{
        // Flush old notifications first; no reset of counters or database-wide fixture deletion.
        await(()->{publisher.publishOnce();return count("SELECT count(*) FROM ticketing.message_failures WHERE dlq_published_at IS NULL AND dlq_attempts<3")==0;});
        String key="metrics-dlq-"+UUID.randomUUID();kafka.send(topic,key,"{\"invalid\":true}").get(8,TimeUnit.SECONDS);
        await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=?",key)==2);
        double beforeRetries=retries("dlq","none"),beforeFailed=dlqDeliveries("FAILED"),beforeAck=dlqDeliveries("ACKNOWLEDGED");
        String container=System.getenv("KAFKA_IT_CONTAINER");assertTrue(container.startsWith("ticketing-kafka-it-"));
        docker("stop",container);
        try{
            publisher.publishOnce();assertEquals(2,count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=? AND dlq_attempts=1 AND dlq_published_at IS NULL",key));
            assertEquals(beforeFailed+2,dlqDeliveries("FAILED"));assertEquals(beforeRetries,retries("dlq","none"));
        }finally{docker("start",container);}
        waitBroker(container);available();jdbc.update("UPDATE ticketing.message_failures SET dlq_next_attempt_at=clock_timestamp() WHERE record_key=?",key);
        publisher.publishOnce();assertEquals(2,count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=? AND dlq_attempts=2 AND dlq_published_at IS NOT NULL",key));
        assertEquals(beforeRetries+2,retries("dlq","none"));assertEquals(beforeAck+2,dlqDeliveries("ACKNOWLEDGED"));
        await(()->gauge("ticketing.messaging.dlq.messages","state","PUBLISHED")==count("SELECT count(*) FROM ticketing.message_failures WHERE dlq_published_at IS NOT NULL"));
        evidence.add(Map.of("case","T8.2 real DLQ failure then retry","failedDeliveries",dlqDeliveries("FAILED")-beforeFailed,"retryDeliveries",retries("dlq","none")-beforeRetries,"acknowledgedDeliveries",dlqDeliveries("ACKNOWLEDGED")-beforeAck,"result","PASS"));
    }
    @Test @Order(8) void replayRoleProfileAuditValidationAndBoundedBudget() throws Exception {
        UUID b=booking(1),id=eventId(b,"BookingHeld"),request=UUID.randomUUID();String path="/api/admin/operations/events/"+id+"/replay";
        snap("replay-security-budget","before");
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
        snap("replay-security-budget","budget-exhausted");
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
    @Test @Order(9) void timelyDurablePaymentWinsExpiryEvenWhileKafkaHasNotDelivered() throws Exception {
        String container=System.getenv("KAFKA_IT_CONTAINER");assertTrue(container!=null&&container.startsWith("ticketing-kafka-it-"));
        UUID b=booking(2);snap("timely-payment-broker-down","before");
        UUID paymentEvent;
        docker("stop",container);
        try {
            health("/readiness",503,"DOWN");health("/liveness",200,"UP");
            // The ledger really commits while the broker is stopped, rather than merely delaying publish.
            var result=record(b,"SUCCESS");paymentEvent=eventId(b,"PaymentResultRecorded");
            jdbc.update("UPDATE ticketing.bookings SET expires_at=(SELECT recorded_at+interval '1ms' FROM ticketing.payments WHERE id=?) WHERE id=?",result.paymentId(),b);
            await(()->jdbc.queryForObject("SELECT expires_at<clock_timestamp() FROM ticketing.bookings WHERE id=?",Boolean.class,b));
            assertEquals(0,inbox(EventConsumer.PAYMENT,paymentEvent));snap("timely-payment-broker-down","durable-offline");
            expiry.runOnce();assertEquals("CONFIRMED",state(b));assertEquals(2,tickets(b));
            assertEquals(0,inbox(EventConsumer.PAYMENT,paymentEvent));
            assertEquals(0,count("SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id=? AND type='BookingExpired'",b));
            snap("timely-payment-broker-down","worker-confirmed-offline");
        } finally {docker("start",container);}
        waitBroker(container);health("/readiness",200,"UP");flush(b);await(()->inbox(EventConsumer.PAYMENT,paymentEvent)==1);
        assertEquals(2,tickets(b));assertEquals(2,count("SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
        assertEquals(1,count("SELECT count(*) FROM ticketing.booking_status_history WHERE booking_id=? AND to_status='CONFIRMED'",b));
        snap("timely-payment-broker-down","recovered");
        evidence.add(Map.of("case","T9.3 real broker stopped: durable timely payment processed by expiry","tickets",2,"expiredTransitions",0,"confirmTransitions",1,"paymentInboxAfterRecovery",1));
    }
    @Test @Order(10) void expiredLateSuccessRefundRetryHistoryAndConcurrentDuplicateAreIdempotent() throws Exception {
        UUID b=booking(2);snap("late-payment-refund","before");due(b);expiry.runOnce();assertEquals("EXPIRED",state(b));snap("late-payment-refund","expired");var result=record(b,"SUCCESS");flush(b);await(()->state(b).equals("REFUND_REQUIRED"));
        UUID comp=jdbc.queryForObject("SELECT id FROM ticketing.payment_compensations WHERE payment_id=?",UUID.class,result.paymentId());
        assertFalse(refunds.attempt(comp));assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM ticketing.payment_compensations WHERE id=?",String.class,comp));
        snap("late-payment-refund","first-refund-failed");
        jdbc.update("UPDATE ticketing.payment_compensations SET next_attempt_at=clock_timestamp() WHERE id=?",comp);
        try(var pool=Executors.newFixedThreadPool(6)){var jobs=new ArrayList<Callable<Boolean>>();for(int i=0;i<12;i++)jobs.add(()->refunds.attempt(comp));int success=0;for(var f:pool.invokeAll(jobs))if(f.get())success++;assertEquals(1,success);}
        for(int i=0;i<5;i++)send(publisher.event(eventId(b,"PaymentResultRecorded")));refunds.runOnce();
        assertEquals(1,count("SELECT count(*) FROM ticketing.payment_compensations WHERE payment_id=?",result.paymentId()));
        assertEquals(2,count("SELECT count(*) FROM ticketing.compensation_attempts WHERE compensation_id=?",comp));
        assertEquals(1,count("SELECT count(*) FROM ticketing.compensation_attempts WHERE compensation_id=? AND outcome='COMPLETED'",comp));
        assertEquals("REFUND_REQUIRED",state(b));assertEquals(0,tickets(b));assertEquals(0,count("SELECT held_count+sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",type));
        snap("late-payment-refund","retried-completed");
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
    void health(String path,int status,String expected)throws Exception{
        var response=http("GET","/actuator/health"+path,null,null);assertEquals(status,response.statusCode(),response.body());
        var body=json.readTree(response.body());assertEquals(expected,body.path("status").asString());assertFalse(body.has("components"));assertFalse(body.has("details"));
    }
    List<tools.jackson.databind.JsonNode> traceLines(CapturedOutput output,UUID eventId){
        return output.getOut().lines().filter(s->s.startsWith("{")).map(s->{try{return json.readTree(s);}catch(Exception ex){throw new AssertionError("Invalid JSON log",ex);}})
            .filter(n->n.path("eventId").asString().equals(eventId.toString())&&n.has("stage")).toList();
    }
    @Test @Order(16) void httpOutboxAndRealKafkaCarrySameIdsAndSafeJson(CapturedOutput output)throws Exception{
        UUID correlation=UUID.randomUUID();String secret="private-payment-"+UUID.randomUUID();
        var body=json.writeValueAsString(Map.of("eventId",event,"ticketTypeId",type,"quantity",1));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/bookings?private="+secret))
            .header("Content-Type","application/json").header("Authorization","Bearer demo-customer-token")
            .header("Idempotency-Key","trace-"+UUID.randomUUID()).header("X-Correlation-Id",correlation.toString())
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(202,response.statusCode(),response.body());
        assertEquals(correlation.toString(),response.headers().firstValue("X-Correlation-Id").orElseThrow());
        UUID booking=UUID.fromString(json.readTree(response.body()).path("bookingId").asString());UUID message=eventId(booking,"BookingHeld");
        assertEquals(correlation,publisher.event(message).correlationId());flush(booking);await(()->inbox(EventConsumer.AUDIT,message)==1);
        await(()->traceLines(output,message).stream().anyMatch(n->n.path("stage").asString().equals("consumer_processed")));
        var lines=traceLines(output,message);var stages=lines.stream().map(n->n.path("stage").asString()).collect(java.util.stream.Collectors.toSet());
        assertTrue(stages.containsAll(Set.of("http_request","outbox_committed","outbox_published","consumer_processed")),stages.toString());
        for(var line:lines){assertEquals(correlation.toString(),line.path("correlationId").asString());assertEquals(booking.toString(),line.path("bookingId").asString());}
        consumer.accept(EventConsumer.AUDIT,booking.toString(),codec.encode(publisher.event(message)));
        assertTrue(traceLines(output,message).stream().anyMatch(n->n.path("outcome").asString().equals("DUPLICATE")));
        // Replay links the original message without replacing its durable correlation ID.
        UUID retryCorrelation=UUID.randomUUID();var retry=http.send(HttpRequest.newBuilder(request.uri())
            .header("Content-Type","application/json").header("Authorization","Bearer demo-customer-token")
            .header("Idempotency-Key",request.headers().firstValue("Idempotency-Key").orElseThrow()).header("X-Correlation-Id",retryCorrelation.toString())
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(202,retry.statusCode());assertEquals(response.body(),retry.body());assertEquals(correlation,publisher.event(message).correlationId());
        assertTrue(traceLines(output,message).stream().anyMatch(n->n.path("stage").asString().equals("http_request")&&n.path("correlationId").asString().equals(retryCorrelation.toString())));
        // Payment uses a new request correlation, including the actual payment consumer path.
        var paymentResponse=http("POST","/api/bookings/"+booking+"/simulate-payment",json.writeValueAsString(Map.of("scenario","SUCCESS","requestId",UUID.randomUUID())),"demo-customer-token");
        assertEquals(202,paymentResponse.statusCode(),paymentResponse.body());String paymentCorrelation=paymentResponse.headers().firstValue("X-Correlation-Id").orElseThrow();
        UUID paymentMessage=eventId(booking,"PaymentResultRecorded");flush(booking);await(()->state(booking).equals("CONFIRMED"));
        await(()->traceLines(output,paymentMessage).stream().anyMatch(n->n.path("consumer").asString().equals(EventConsumer.PAYMENT)&&n.path("stage").asString().equals("consumer_processed")));
        var paymentLines=traceLines(output,paymentMessage);assertTrue(paymentLines.stream().map(n->n.path("stage").asString()).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of("outbox_committed","outbox_published","consumer_processed")));
        for(var line:paymentLines){assertEquals(paymentCorrelation,line.path("correlationId").asString());assertEquals(booking.toString(),line.path("bookingId").asString());}
        assertTrue(output.getOut().lines().filter(s->s.startsWith("{")).map(json::readTree).anyMatch(n->n.path("stage").asString().equals("http_request")&&n.path("correlationId").asString().equals(paymentCorrelation)&&n.path("bookingId").asString().equals(booking.toString())));
        // A real poison record must be retried/parked without dumping its raw payment/token fields.
        String poisonKey="poison-trace-"+UUID.randomUUID();kafka.send(topic,poisonKey,"{\"token\":\""+secret+"\",\"paymentPayload\":\""+secret+"\"}").get(8,TimeUnit.SECONDS);
        await(()->count("SELECT count(*) FROM ticketing.message_failures WHERE record_key=?",poisonKey)==2);
        assertFalse(output.getAll().contains(secret));assertFalse(output.getAll().contains("demo-customer-token"));assertFalse(output.getAll().contains("paymentPayload"));
        // A separate request has a different correlation and no stale booking/message IDs.
        var next=http("GET","/api/events",null,null);String nextId=next.headers().firstValue("X-Correlation-Id").orElseThrow();
        var nextLog=output.getOut().lines().filter(s->s.startsWith("{")).map(json::readTree).filter(n->n.path("stage").asString().equals("http_request")&&n.path("correlationId").asString().equals(nextId)).findFirst().orElseThrow();
        assertFalse(nextLog.has("bookingId"));assertFalse(nextLog.has("eventId"));
        evidence.add(Map.of("case","T8.1 HTTP/outbox/Kafka JSON trace and poison redaction","correlationId",correlation,"bookingId",booking,"eventId",message,"paymentCorrelationId",paymentCorrelation,"paymentEventId",paymentMessage,"stages",stages,"result","PASS"));
    }
}
