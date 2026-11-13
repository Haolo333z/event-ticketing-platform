package com.haolo.ticketing.messaging;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.haolo.ticketing.bookings.BookingTransactions;
import com.haolo.ticketing.operations.TraceContext;
import com.haolo.ticketing.operations.TraceLog;

@Service
public class FailureStore {
    private final JdbcTemplate jdbc;private final BookingTransactions tx;private final EventCodec codec;
    public FailureStore(JdbcTemplate jdbc,BookingTransactions tx,EventCodec codec){this.jdbc=jdbc;this.tx=tx;this.codec=codec;}
    public void park(String consumer,ConsumerRecord<?,?> record,Exception error){
        String raw=record.value()==null?null:record.value().toString(),key=record.key()==null?null:record.key().toString();
        OutboxWriter.Event parsed=null;try{parsed=codec.decode(raw,key);}catch(RuntimeException ignored){/* retain raw poison record */}
        UUID eventId=parsed==null?null:parsed.eventId();
        // Never persist exception messages that might include raw payloads or credentials.
        tx.run(()->{jdbc.update("INSERT INTO ticketing.message_failures(id,consumer,topic,partition_id,record_offset,record_key,raw_event,event_id,attempts,error) VALUES (?,?,?,?,?,?,?,?,3,?) ON CONFLICT(consumer,topic,partition_id,record_offset) DO NOTHING",UUID.randomUUID(),consumer,record.topic(),record.partition(),record.offset(),key,raw,eventId,error.getClass().getSimpleName());return null;});
        try(var context=TraceContext.open(parsed==null?null:parsed.correlationId(),parsed==null?null:parsed.aggregateId(),eventId)){
            TraceLog.event("consumer_parked","DEAD",consumer);
        }
    }
}
