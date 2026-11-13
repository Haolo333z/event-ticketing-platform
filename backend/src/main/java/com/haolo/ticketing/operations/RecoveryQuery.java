package com.haolo.ticketing.operations;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

/** Diagnostics only: no raw record/payload/error text and no changes to the recovery state. */
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class RecoveryQuery {
    public record Item(UUID id,String source,UUID eventId,UUID bookingId,String eventType,String consumer,String status,String error,String createdAt,String dlqStatus,boolean replayable){}
    public record Page(List<Item> items,int page,int size,int totalElements){}
    public record Failure(UUID id,String consumer,String status,String error,int attempts,String createdAt,String resolvedAt,String dlqStatus,int dlqAttempts){}
    public record Receipt(String consumer,String processedAt){}
    public record Audit(UUID requestId,UUID adminAccountId,String reason,String requestedAt){}
    public record Detail(UUID eventId,UUID bookingId,String eventType,long aggregateVersion,UUID correlationId,String status,String createdAt,String publishedAt,int attempts,String publisherError,int remainingReplays,List<Audit> audit,List<Receipt> consumers,List<Failure> failures){}
    private final JdbcTemplate jdbc;private final ReplayService replay;private final ObjectMapper json;
    public RecoveryQuery(JdbcTemplate jdbc,ReplayService replay,ObjectMapper json){this.jdbc=jdbc;this.replay=replay;this.json=json;}
    private static final String ROWS="""
        SELECT o.id,'OUTBOX' AS source,o.id AS event_id,o.aggregate_id AS booking_id,o.type AS event_type,
            NULL::text AS consumer,o.status,o.last_error AS error,o.created_at,NULL::text AS dlq_status,
            jsonb_array_length(o.replay_requests)<3 AS replayable
        FROM ticketing.outbox_events o WHERE o.status='FAILED' OR o.last_error IS NOT NULL OR jsonb_array_length(o.replay_requests)>0
        UNION ALL
        SELECT f.id,'CONSUMER',f.event_id,o.aggregate_id,o.type,f.consumer,f.status,f.error,f.created_at,
            CASE WHEN f.dlq_published_at IS NOT NULL THEN 'PUBLISHED' WHEN f.dlq_attempts>=3 THEN 'EXHAUSTED' ELSE 'PENDING' END,
            o.id IS NOT NULL AND jsonb_array_length(o.replay_requests)<3
        FROM ticketing.message_failures f LEFT JOIN ticketing.outbox_events o ON o.id=f.event_id
        """;
    public Page list(DemoPrincipal principal,int page,int size,String status){
        replay.authorize(principal);
        if(page<0||page>10000||size<1||size>100||status==null||!Set.of("OPEN","RESOLVED","ALL").contains(status))throw ApiException.invalid();
        String filter=switch(status){case "OPEN" -> " WHERE status IN ('FAILED','PENDING','DEAD')";case "RESOLVED" -> " WHERE status IN ('PUBLISHED','RESOLVED')";default -> "";};
        String base=" FROM ("+ROWS+") recovery"+filter;
        int total=jdbc.queryForObject("SELECT count(*)"+base,Integer.class);
        var items=jdbc.query("SELECT *"+base+" ORDER BY created_at DESC,id DESC,source LIMIT ? OFFSET ?",(r,n)->new Item(r.getObject("id",UUID.class),r.getString("source"),r.getObject("event_id",UUID.class),r.getObject("booking_id",UUID.class),r.getString("event_type"),consumer(r.getString("consumer")),r.getString("status"),safeError(r.getString("source"),r.getString("status"),r.getString("error")),r.getTimestamp("created_at").toInstant().toString(),r.getString("dlq_status"),r.getBoolean("replayable")),size,page*size);
        return new Page(items,page,size,total);
    }
    public Detail event(DemoPrincipal principal,UUID id){
        replay.authorize(principal);var rows=jdbc.queryForList("SELECT id,aggregate_id,type,aggregate_version,correlation_id,status,created_at,published_at,attempts,last_error,replay_requests::text AS audit FROM ticketing.outbox_events WHERE id=?",id);
        if(rows.isEmpty())throw ApiException.missing();var row=rows.getFirst();
        var audit=json.readValue((String)row.get("audit"),new TypeReference<List<Audit>>(){});
        var receipts=jdbc.query("SELECT consumer,processed_at FROM ticketing.inbox_events WHERE event_id=? ORDER BY consumer",(r,n)->new Receipt(consumer(r.getString("consumer")),r.getTimestamp("processed_at").toInstant().toString()),id);
        var failures=jdbc.query("SELECT id,consumer,status,error,attempts,created_at,resolved_at,dlq_attempts,dlq_published_at FROM ticketing.message_failures WHERE event_id=? ORDER BY created_at DESC,id DESC LIMIT 100",(r,n)->new Failure(r.getObject("id",UUID.class),consumer(r.getString("consumer")),r.getString("status"),safeError("CONSUMER",r.getString("status"),r.getString("error")),r.getInt("attempts"),r.getTimestamp("created_at").toInstant().toString(),time(r.getTimestamp("resolved_at")),r.getTimestamp("dlq_published_at")!=null?"PUBLISHED":r.getInt("dlq_attempts")>=3?"EXHAUSTED":"PENDING",r.getInt("dlq_attempts")),id);
        return new Detail(id,(UUID)row.get("aggregate_id"),(String)row.get("type"),((Number)row.get("aggregate_version")).longValue(),(UUID)row.get("correlation_id"),(String)row.get("status"),time((java.sql.Timestamp)row.get("created_at")),time((java.sql.Timestamp)row.get("published_at")),((Number)row.get("attempts")).intValue(),safeError("OUTBOX",(String)row.get("status"),(String)row.get("last_error")),Math.max(0,3-audit.size()),audit,receipts,failures);
    }
    private static String time(java.sql.Timestamp value){return value==null?null:value.toInstant().toString();}
    private static String consumer(String value){return value==null?null:Set.of("payment-v1","booking-audit-v1").contains(value)?value:"UNKNOWN";}
    private static String safeError(String source,String status,String stored){
        if(source.equals("OUTBOX"))return stored!=null||status.equals("FAILED")?"Kafka chưa xác nhận gửi; kiểm tra broker và log theo correlation ID.":"Không có lỗi publisher hiện tại.";
        String type=stored!=null&&Set.of("ListenerExecutionFailedException","ApiException","IllegalArgumentException","IllegalStateException").contains(stored)?" ("+stored+")":"";
        return "Consumer xử lý thất bại sau 3 lần"+type+"; kiểm tra dữ liệu bền, thứ tự version và log. Không sửa raw record tại đây.";
    }
}
