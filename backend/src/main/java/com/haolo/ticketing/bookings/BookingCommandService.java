package com.haolo.ticketing.bookings;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.events.CatalogBookingAccess;
import com.haolo.ticketing.inventory.GaInventory;
import com.haolo.ticketing.messaging.OutboxWriter;
import tools.jackson.databind.ObjectMapper;

@Service
public class BookingCommandService {
    public record Result(UUID bookingId,String body){}
    private final JdbcTemplate jdbc;private final PlatformTransactionManager manager;private final BookingPolicy policy;
    private final CatalogBookingAccess catalog;private final GaInventory inventory;private final OutboxWriter outbox;private final ObjectMapper mapper;
    public BookingCommandService(JdbcTemplate jdbc,PlatformTransactionManager manager,BookingPolicy policy,CatalogBookingAccess catalog,GaInventory inventory,OutboxWriter outbox,ObjectMapper mapper){
        this.jdbc=jdbc;this.manager=manager;this.policy=policy;this.catalog=catalog;this.inventory=inventory;this.outbox=outbox;this.mapper=mapper;
    }
    public Result create(DemoPrincipal principal,String key,GaBookingRequest request,UUID correlationId){
        if(principal==null||!principal.role().equals("CUSTOMER"))throw new ApiException(403,"FORBIDDEN","Chỉ khách hàng được tạo booking");
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}")||request.quantity()>policy.maxQuantity())throw ApiException.invalid();
        long deadline=System.nanoTime()+policy.totalMs()*1_000_000L;
        for(int attempt=0;attempt<3;attempt++){
            long remaining=(deadline-System.nanoTime())/1_000_000L;
            if(remaining<policy.poolMs()+1000)throw busy();
            var tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.setTimeout((int)Math.min(policy.attemptMs()/1000,(remaining-policy.poolMs())/1000));
            try{return tx.execute(status->execute(principal.accountId(),key,request,correlationId));}
            catch(ApiException ex){throw ex;}
            catch(RuntimeException ex){
                String state=sqlState(ex);
                if(("40P01".equals(state)||"40001".equals(state))&&attempt<2){
                    try {Thread.sleep((50L<<attempt)+ThreadLocalRandom.current().nextInt(20));}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw busy();}
                    continue;
                }
                if(state!=null&&(Set.of("55P03","57014","40P01","40001").contains(state)||state.startsWith("08")))throw busy();
                if(ex instanceof CannotCreateTransactionException||ex instanceof TransactionTimedOutException||ex instanceof org.springframework.dao.TransientDataAccessException)throw busy();
                throw new ApiException(500,"INTERNAL_ERROR","Không thể hoàn tất giao dịch đặt vé");
            }
        }
        throw busy();
    }
    private Result execute(UUID account,String key,GaBookingRequest request,UUID correlationId){
        jdbc.execute("SET LOCAL lock_timeout='"+policy.lockMs()+"ms'");
        jdbc.execute("SET LOCAL statement_timeout='"+policy.statementMs()+"ms'");
        String hash=request.hash();
        int inserted=jdbc.update("INSERT INTO ticketing.idempotency_keys(account_id,operation,key,request_hash) VALUES (?,'CREATE_BOOKING',?,?) ON CONFLICT DO NOTHING",account,key,hash);
        if(inserted==0){
            var row=jdbc.queryForMap("SELECT request_hash,booking_id,response_body::text AS body FROM ticketing.idempotency_keys WHERE account_id=? AND operation='CREATE_BOOKING' AND key=?",account,key);
            if(!hash.equals(row.get("request_hash")))throw new ApiException(409,"IDEMPOTENCY_KEY_REUSED","Mã yêu cầu đã được dùng cho nội dung khác");
            return new Result((UUID)row.get("booking_id"),(String)row.get("body"));
        }
        long price=catalog.lockGaPrice(request.eventId(),request.ticketTypeId());long total=Math.multiplyExact(price,request.quantity());
        if(total>1000000000000L)throw ApiException.invalid();
        UUID booking=UUID.randomUUID();
        var times=jdbc.queryForMap("INSERT INTO ticketing.bookings(id,account_id,event_id,ticket_type_id,payment_intent_id,status,created_at,expires_at,total_amount_vnd,aggregate_version) SELECT ?,?,?,?,?, 'PENDING_PAYMENT',t.now,t.now+make_interval(secs=>?),?,1 FROM (SELECT clock_timestamp() AS now) t RETURNING created_at,expires_at",
            booking,account,request.eventId(),request.ticketTypeId(),UUID.randomUUID(),policy.holdSeconds(),total);
        inventory.reserve(request.ticketTypeId(),request.quantity());
        jdbc.update("INSERT INTO ticketing.booking_items(id,booking_id,event_id,ticket_type_id,quantity,unit_price_vnd,total_price_vnd) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),booking,request.eventId(),request.ticketTypeId(),request.quantity(),price,total);
        String expires=((java.sql.Timestamp)times.get("expires_at")).toInstant().toString();
        var event=outbox.append(booking,1,"BookingHeld",correlationId,Map.of("bookingId",booking.toString(),"expiresAt",expires));
        jdbc.update("INSERT INTO ticketing.booking_status_history(id,booking_id,from_status,to_status,occurred_at,cause_event_id) VALUES (?,?,NULL,'PENDING_PAYMENT',?,?)",UUID.randomUUID(),booking,times.get("created_at"),event.eventId());
        String body=mapper.writeValueAsString(Map.of("bookingId",booking,"status","PENDING_PAYMENT","expiresAt",expires,"totalAmountVnd",total));
        String saved=jdbc.queryForObject("UPDATE ticketing.idempotency_keys SET booking_id=?,response_status=202,response_body=CAST(? AS jsonb) WHERE account_id=? AND operation='CREATE_BOOKING' AND key=? RETURNING response_body::text",String.class,booking,body,account,key);
        return new Result(booking,saved);
    }
    static String sqlState(Throwable ex){for(Throwable t=ex;t!=null;t=t.getCause())if(t instanceof java.sql.SQLException sql)return sql.getSQLState();return null;}
    private static ApiException busy(){return new ApiException(503,"BOOKING_BUSY","Hệ thống đang bận; hãy thử lại cùng mã yêu cầu");}
}
