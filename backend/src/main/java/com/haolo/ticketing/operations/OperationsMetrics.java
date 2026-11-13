package com.haolo.ticketing.operations;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.sql.DataSource;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Pull committed state independently of all business workers; one bounded read-only DB snapshot. */
@Component
public class OperationsMetrics implements MeterBinder {
    static final String SQL="""
        SELECT 'booking.'||status AS key,count(*)::float8 AS value FROM ticketing.bookings GROUP BY status
        UNION ALL SELECT 'overdue.count',count(*)::float8 FROM ticketing.bookings WHERE status='PENDING_PAYMENT' AND expires_at<=statement_timestamp()
        UNION ALL SELECT 'overdue.age',coalesce(greatest(extract(epoch FROM statement_timestamp()-min(expires_at)),0),0)::float8 FROM ticketing.bookings WHERE status='PENDING_PAYMENT' AND expires_at<=statement_timestamp()
        UNION ALL SELECT 'outbox.'||status,count(*)::float8 FROM ticketing.outbox_events GROUP BY status
        UNION ALL SELECT 'outbox.age',coalesce(greatest(extract(epoch FROM statement_timestamp()-min(created_at)),0),0)::float8 FROM ticketing.outbox_events WHERE status IN ('PENDING','FAILED')
        UNION ALL SELECT 'outbox.attempts',coalesce(sum(attempts),0)::float8 FROM ticketing.outbox_events
        UNION ALL SELECT 'failure.'||consumer||'.'||status,count(*)::float8 FROM ticketing.message_failures GROUP BY consumer,status
        UNION ALL SELECT 'dlq.'||CASE WHEN dlq_published_at IS NOT NULL THEN 'PUBLISHED' WHEN dlq_attempts>=3 THEN 'EXHAUSTED' ELSE 'PENDING' END,count(*)::float8 FROM ticketing.message_failures GROUP BY 1
        """;
    private record Sample(Map<String,Double> values,boolean valid){}
    private final Supplier<Map<String,Double>> query;
    private final long ttlNanos;
    private final LongSupplier clock;
    private Sample sample;
    private long sampledAt,lastSuccess=Long.MIN_VALUE;

    @Autowired public OperationsMetrics(DataSource source,@Value("${operations.metrics.snapshot-ttl-ms:1000}")long ttlMs){this(()->read(source),ttlMs,System::nanoTime);}
    OperationsMetrics(Supplier<Map<String,Double>> query,long ttlMs,LongSupplier clock){
        if(ttlMs<0||ttlMs>5000)throw new IllegalArgumentException("Invalid metrics snapshot TTL");
        this.query=query;this.ttlNanos=ttlMs*1_000_000L;this.clock=clock;
    }
    private static Map<String,Double> read(DataSource source){
        // Deliberately bypass transaction-bound DataSourceUtils/JdbcTemplate. A scrape must not
        // publish uncommitted state even when invoked on a thread running a business transaction.
        try(var connection=source.getConnection()){
            connection.setReadOnly(true);
            try(var statement=connection.prepareStatement(SQL)){
                statement.setQueryTimeout(2);var values=new HashMap<String,Double>();
                try(var rows=statement.executeQuery()){while(rows.next())values.put(rows.getString("key"),rows.getDouble("value"));}
                return Map.copyOf(values);
            }
        }catch(java.sql.SQLException ex){throw new IllegalStateException("Metrics snapshot unavailable");}
    }
    private synchronized Sample current(){
        long now=clock.getAsLong();
        if(sample!=null&&now-sampledAt<ttlNanos)return sample;
        try{
            sample=new Sample(Map.copyOf(query.get()),true);lastSuccess=clock.getAsLong();
        }catch(RuntimeException ex){sample=new Sample(Map.of(),false);/* No SQL/error text or fake healthy zeros. */}
        sampledAt=clock.getAsLong();return sample;
    }
    double value(String key){var snapshot=current();return snapshot.valid()?snapshot.values().getOrDefault(key,0d):Double.NaN;}
    private synchronized double available(){return current().valid()?1:0;}
    private synchronized double age(){current();return lastSuccess==Long.MIN_VALUE?Double.NaN:Math.max(0,(clock.getAsLong()-lastSuccess)/1_000_000_000d);}
    private void gauge(MeterRegistry registry,String name,String key,String unit,String description,String... tags){
        Gauge.builder(name,this,m->m.value(key)).tags(tags).baseUnit(unit).description(description).register(registry);
    }
    @Override public void bindTo(MeterRegistry registry){
        for(String status:List.of("PENDING_PAYMENT","CONFIRMED","PAYMENT_FAILED","CANCELLED","EXPIRED","REFUND_REQUIRED"))
            gauge(registry,"ticketing.bookings","booking."+status,"bookings","Current committed bookings by state","status",status);
        gauge(registry,"ticketing.holds.overdue","overdue.count","bookings","Pending bookings past the DB-clock hold deadline");
        gauge(registry,"ticketing.holds.overdue.oldest.age","overdue.age","seconds","Seconds since the oldest overdue hold deadline; zero when empty");
        for(String status:List.of("PENDING","PUBLISHED","FAILED"))gauge(registry,"ticketing.outbox.messages","outbox."+status,"messages","Current durable outbox rows","status",status);
        gauge(registry,"ticketing.outbox.unpublished.oldest.age","outbox.age","seconds","Seconds since oldest PENDING or FAILED outbox row; zero when empty");
        gauge(registry,"ticketing.outbox.attempts","outbox.attempts","attempts","Sum of stored attempts; replay can reset this gauge");
        for(String consumer:List.of("payment-v1","booking-audit-v1"))for(String status:List.of("DEAD","RESOLVED"))
            gauge(registry,"ticketing.messaging.failures","failure."+consumer+"."+status,"messages","Durable recovery rows","consumer",consumer,"status",status);
        for(String state:List.of("PENDING","PUBLISHED","EXHAUSTED"))gauge(registry,"ticketing.messaging.dlq.messages","dlq."+state,"messages","Durable DLQ notification state, including resolved recovery rows","state",state);
        Gauge.builder("ticketing.metrics.snapshot.available",this,OperationsMetrics::available).baseUnit("boolean").description("One if latest DB snapshot succeeded; zero on failure").register(registry);
        Gauge.builder("ticketing.metrics.snapshot.age",this,OperationsMetrics::age).baseUnit("seconds").description("JVM seconds since last successful DB snapshot; NaN before first success").register(registry);
    }
}
