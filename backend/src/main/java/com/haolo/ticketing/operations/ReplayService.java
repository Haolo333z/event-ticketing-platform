package com.haolo.ticketing.operations;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import com.haolo.ticketing.bookings.BookingTransactions;
import com.haolo.ticketing.identity.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class ReplayService {
    public record Accepted(UUID eventId,UUID requestId,String status){}
    public record Outbox(UUID eventId,UUID bookingId,String eventType,long aggregateVersion,String status,String createdAt,int attempts){}
    public record Page(List<Outbox> items,int page,int size,int totalElements){}
    private final JdbcTemplate jdbc;private final BookingTransactions tx;private final ObjectMapper json;private final DemoSettings settings;
    public ReplayService(JdbcTemplate jdbc,BookingTransactions tx,ObjectMapper json,DemoSettings settings){this.jdbc=jdbc;this.tx=tx;this.json=json;this.settings=settings;}
    void authorize(DemoPrincipal principal){if(!settings.safeProfile||principal==null||!principal.role().equals("ADMIN"))throw new ApiException(403,"FORBIDDEN","Replay chỉ dành cho admin local/test");}
    public Page list(DemoPrincipal principal,int page,int size){
        authorize(principal);if(page<0||page>10000||size<1||size>100)throw ApiException.invalid();
        var items=jdbc.query("SELECT * FROM ticketing.outbox_events ORDER BY created_at DESC,id LIMIT ? OFFSET ?",(r,n)->new Outbox(r.getObject("id",UUID.class),r.getObject("aggregate_id",UUID.class),r.getString("type"),r.getLong("aggregate_version"),r.getString("status"),r.getTimestamp("created_at").toInstant().toString(),r.getInt("attempts")),size,page*size);
        return new Page(items,page,size,jdbc.queryForObject("SELECT count(*) FROM ticketing.outbox_events",Integer.class));
    }
    public Accepted replay(DemoPrincipal principal,UUID event,UUID request,String reason){
        authorize(principal);if(request==null||reason==null||reason.isBlank()||reason.codePointCount(0,reason.length())>500)throw ApiException.invalid();
        return tx.run(()->{
            var rows=jdbc.queryForList("SELECT replay_requests::text AS audit FROM ticketing.outbox_events WHERE id=? FOR UPDATE",event);
            if(rows.isEmpty())throw ApiException.missing();
            var audit=json.readValue((String)rows.getFirst().get("audit"),new TypeReference<List<Map<String,Object>>>(){});
            for(var previous:audit)if(request.toString().equals(previous.get("requestId"))){
                if(!reason.equals(previous.get("reason")))throw new ApiException(409,"IDEMPOTENCY_KEY_REUSED","requestId đã dùng cho nội dung khác");
                return new Accepted(event,request,"ACCEPTED");
            }
            if(audit.size()>=3)throw new ApiException(409,"REPLAY_LIMIT_REACHED","Đã hết ngân sách replay");
            String now=jdbc.queryForObject("SELECT clock_timestamp()",(r,n)->r.getTimestamp(1).toInstant().toString());
            audit.add(Map.of("requestId",request.toString(),"adminAccountId",principal.accountId().toString(),"reason",reason,"requestedAt",now));
            jdbc.update("UPDATE ticketing.outbox_events SET replay_requests=?::jsonb,status='PENDING',attempts=0,next_attempt_at=clock_timestamp(),last_error=NULL WHERE id=?",json.writeValueAsString(audit),event);
            return new Accepted(event,request,"ACCEPTED");
        });
    }
}
