package com.haolo.ticketing.bookings;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.events.CatalogBookingAccess;
import com.haolo.ticketing.inventory.GaInventory;
import com.haolo.ticketing.inventory.SeatInventory;
import com.haolo.ticketing.messaging.OutboxWriter;
import tools.jackson.databind.ObjectMapper;

@Service
public class BookingCommandService {
    public record Result(UUID bookingId,String body,UUID eventId){}
    private final JdbcTemplate jdbc;private final BookingTransactions transactions;private final BookingPolicy policy;
    private final CatalogBookingAccess catalog;private final GaInventory inventory;private final OutboxWriter outbox;private final ObjectMapper mapper;
    private final SeatInventory seats;
    public BookingCommandService(JdbcTemplate jdbc,BookingTransactions transactions,BookingPolicy policy,CatalogBookingAccess catalog,GaInventory inventory,SeatInventory seats,OutboxWriter outbox,ObjectMapper mapper){
        this.jdbc=jdbc;this.transactions=transactions;this.policy=policy;this.catalog=catalog;this.inventory=inventory;this.seats=seats;this.outbox=outbox;this.mapper=mapper;
    }
    public Result create(DemoPrincipal principal,String key,BookingRequest request,UUID correlationId){
        if(principal==null||!principal.role().equals("CUSTOMER"))throw new ApiException(403,"FORBIDDEN","Chỉ khách hàng được tạo booking");
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}")||request.quantity()>policy.maxQuantity())throw ApiException.invalid();
        return transactions.run(()->execute(principal.accountId(),key,request,correlationId));
    }
    private Result execute(UUID account,String key,BookingRequest request,UUID correlationId){
        String hash=request.hash();
        int inserted=jdbc.update("INSERT INTO ticketing.idempotency_keys(account_id,operation,key,request_hash) VALUES (?,'CREATE_BOOKING',?,?) ON CONFLICT DO NOTHING",account,key,hash);
        if(inserted==0){
            var row=jdbc.queryForMap("SELECT request_hash,booking_id,response_body::text AS body FROM ticketing.idempotency_keys WHERE account_id=? AND operation='CREATE_BOOKING' AND key=?",account,key);
            if(!hash.equals(row.get("request_hash")))throw new ApiException(409,"IDEMPOTENCY_KEY_REUSED","Mã yêu cầu đã được dùng cho nội dung khác");
            UUID booking=(UUID)row.get("booking_id");
            UUID event=jdbc.queryForObject("SELECT id FROM ticketing.outbox_events WHERE aggregate_id=? AND aggregate_version=1",UUID.class,booking);
            return new Result(booking,(String)row.get("body"),event);
        }
        long price=catalog.lockPrice(request.eventId(),request.ticketTypeId(),request.mode());long total=Math.multiplyExact(price,request.quantity());
        if(total>1000000000000L)throw ApiException.invalid();
        UUID booking=UUID.randomUUID();
        var times=jdbc.queryForMap("INSERT INTO ticketing.bookings(id,account_id,event_id,ticket_type_id,payment_intent_id,status,created_at,expires_at,total_amount_vnd,aggregate_version) SELECT ?,?,?,?,?, 'PENDING_PAYMENT',t.now,t.now+make_interval(secs=>?),?,1 FROM (SELECT clock_timestamp() AS now) t RETURNING created_at,expires_at",
            booking,account,request.eventId(),request.ticketTypeId(),UUID.randomUUID(),policy.holdSeconds(),total);
        if(request instanceof SeatBookingRequest selection)seats.reserve(booking,selection,(java.sql.Timestamp)times.get("expires_at"),price);
        else{
            inventory.reserve(request.ticketTypeId(),request.quantity());
            jdbc.update("INSERT INTO ticketing.booking_items(id,booking_id,event_id,ticket_type_id,quantity,unit_price_vnd,total_price_vnd) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),booking,request.eventId(),request.ticketTypeId(),request.quantity(),price,total);
        }
        String expires=((java.sql.Timestamp)times.get("expires_at")).toInstant().toString();
        var event=outbox.append(booking,1,"BookingHeld",correlationId,Map.of("bookingId",booking.toString(),"expiresAt",expires));
        jdbc.update("INSERT INTO ticketing.booking_status_history(id,booking_id,from_status,to_status,occurred_at,cause_event_id) VALUES (?,?,NULL,'PENDING_PAYMENT',?,?)",UUID.randomUUID(),booking,times.get("created_at"),event.eventId());
        String body=mapper.writeValueAsString(Map.of("bookingId",booking,"status","PENDING_PAYMENT","expiresAt",expires,"totalAmountVnd",total));
        String saved=jdbc.queryForObject("UPDATE ticketing.idempotency_keys SET booking_id=?,response_status=202,response_body=CAST(? AS jsonb) WHERE account_id=? AND operation='CREATE_BOOKING' AND key=? RETURNING response_body::text",String.class,booking,body,account,key);
        return new Result(booking,saved,event.eventId());
    }
}
