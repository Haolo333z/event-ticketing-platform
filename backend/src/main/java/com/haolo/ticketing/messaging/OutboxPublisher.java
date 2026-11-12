package com.haolo.ticketing.messaging;

import java.util.*;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import com.haolo.ticketing.bookings.BookingTransactions;

/** Session leader only; send+ACK happens with NO business transaction or row lock. */
@Service @ConditionalOnProperty(name="messaging.enabled",havingValue="true")
public class OutboxPublisher {
    private static final long LEADER=7469636L;
    private final DataSource source;private final JdbcTemplate jdbc;private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper json;private final EventCodec codec;private final String topic,dlq;
    private final BookingTransactions tx;
    public OutboxPublisher(DataSource source,JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,ObjectMapper json,EventCodec codec,BookingTransactions tx,@Value("${messaging.topic}")String topic,@Value("${messaging.dlq-topic}")String dlq){this.source=source;this.jdbc=jdbc;this.kafka=kafka;this.json=json;this.codec=codec;this.tx=tx;this.topic=topic;this.dlq=dlq;}
    public OutboxWriter.Event event(UUID id){
        return jdbc.queryForObject("SELECT * FROM ticketing.outbox_events WHERE id=?",(r,n)->new OutboxWriter.Event(id,r.getString("type"),r.getInt("schema_version"),r.getObject("aggregate_id",UUID.class),r.getLong("aggregate_version"),r.getTimestamp("created_at").toInstant().toString(),r.getObject("correlation_id",UUID.class),json.readValue(r.getString("payload"),new TypeReference<Map<String,Object>>(){})),id);
    }
    public synchronized int publishOnce(){
        try(Connection leader=source.getConnection();var statement=leader.prepareStatement("SELECT pg_try_advisory_lock(?)")){
            statement.setLong(1,LEADER);try(var r=statement.executeQuery()){r.next();if(!r.getBoolean(1))return 0;}
            try{
                var ids=jdbc.queryForList("SELECT o.id FROM ticketing.outbox_events o WHERE o.status='PENDING' AND o.attempts<3 AND o.next_attempt_at<=clock_timestamp() AND NOT EXISTS(SELECT 1 FROM ticketing.outbox_events earlier WHERE earlier.aggregate_id=o.aggregate_id AND earlier.aggregate_version<o.aggregate_version AND earlier.status<>'PUBLISHED') ORDER BY o.created_at,o.id LIMIT 50",UUID.class);
                int sent=0;
                for(UUID id:ids){var event=event(id);boolean ack=false;
                    int generation=jdbc.queryForObject("SELECT jsonb_array_length(replay_requests) FROM ticketing.outbox_events WHERE id=?",Integer.class,id);
                    try{codec.decode(codec.encode(event),event.aggregateId().toString());kafka.send(topic,event.aggregateId().toString(),codec.encode(event)).get(6,java.util.concurrent.TimeUnit.SECONDS);ack=true;}
                    catch(Exception ex){if(ex instanceof InterruptedException){Thread.currentThread().interrupt();return sent;}}
                    // Serialize with admin replay. Do not overwrite a newer replay request (its attempts reset).
                    if(ack){tx.run(()->jdbc.update("UPDATE ticketing.outbox_events SET status='PUBLISHED',attempts=attempts+1,published_at=clock_timestamp(),last_error=NULL WHERE id=? AND jsonb_array_length(replay_requests)=?",id,generation));sent++;}
                    else tx.run(()->jdbc.update("UPDATE ticketing.outbox_events SET attempts=attempts+1,status=CASE WHEN attempts+1>=3 THEN 'FAILED' ELSE 'PENDING' END,next_attempt_at=clock_timestamp()+make_interval(secs=>power(2,attempts)::int),last_error='Kafka send not acknowledged' WHERE id=? AND status='PENDING' AND jsonb_array_length(replay_requests)=?",id,generation));
                }
                publishDlq();return sent;
            }finally{try(var unlock=leader.prepareStatement("SELECT pg_advisory_unlock(?)")){unlock.setLong(1,LEADER);unlock.execute();}}
        }catch(SQLException ex){throw new IllegalStateException("Publisher coordination unavailable",ex);}
    }
    private void publishDlq(){
        for(var row:jdbc.queryForList("SELECT * FROM ticketing.message_failures WHERE dlq_published_at IS NULL AND dlq_attempts<3 AND dlq_next_attempt_at<=clock_timestamp() ORDER BY created_at LIMIT 10")){
            boolean ack=false;try{var payload=new LinkedHashMap<String,Object>();for(String key:List.of("id","consumer","topic","partition_id","record_offset","record_key","raw_event","attempts","error"))payload.put(key,row.get(key));kafka.send(dlq,row.get("id").toString(),json.writeValueAsString(payload)).get(6,java.util.concurrent.TimeUnit.SECONDS);ack=true;}
            catch(Exception ex){if(ex instanceof InterruptedException){Thread.currentThread().interrupt();return;}}
            boolean acknowledged=ack;
            tx.run(()->jdbc.update("UPDATE ticketing.message_failures SET dlq_attempts=dlq_attempts+1,dlq_published_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,dlq_next_attempt_at=clock_timestamp()+make_interval(secs=>power(2,dlq_attempts)::int) WHERE id=?",acknowledged,row.get("id")));
        }
    }
}
