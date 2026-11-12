package com.haolo.ticketing.bookings;

import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class BookingQueryService {
    public record History(String fromStatus,String toStatus,String occurredAt){}
    public record Booking(UUID bookingId,UUID eventId,String status,String expiresAt,String expiredAt,long totalAmountVnd,List<History> history){}
    public record Page(List<Booking> items,int page,int size,long totalElements){}
    private final JdbcTemplate jdbc;
    public BookingQueryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Booking map(ResultSet r,int row) throws SQLException {
        UUID id=r.getObject("id",UUID.class);var expired=r.getTimestamp("expired_at");
        return new Booking(id,r.getObject("event_id",UUID.class),r.getString("status"),r.getTimestamp("expires_at").toInstant().toString(),expired==null?null:expired.toInstant().toString(),r.getLong("total_amount_vnd"),
            jdbc.query("SELECT * FROM ticketing.booking_status_history WHERE booking_id=? ORDER BY occurred_at,id",(h,n)->new History(h.getString("from_status"),h.getString("to_status"),h.getTimestamp("occurred_at").toInstant().toString()),id));
    }
    public Booking get(DemoPrincipal principal,UUID id){
        if(principal==null)throw new ApiException(401,"UNAUTHENTICATED","Cần đăng nhập");
        boolean admin=principal.role().equals("ADMIN");
        var rows=admin?jdbc.query("SELECT * FROM ticketing.bookings WHERE id=?",this::map,id):jdbc.query("SELECT * FROM ticketing.bookings WHERE id=? AND account_id=?",this::map,id,principal.accountId());
        if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    public Page mine(DemoPrincipal principal,int page,int size){
        if(principal==null||!principal.role().equals("CUSTOMER"))throw new ApiException(403,"FORBIDDEN","Chỉ khách hàng có lịch sử cá nhân");
        if(page<0||page>10000||size<1||size>100)throw ApiException.invalid();
        long count=jdbc.queryForObject("SELECT count(*) FROM ticketing.bookings WHERE account_id=?",Long.class,principal.accountId());
        return new Page(jdbc.query("SELECT * FROM ticketing.bookings WHERE account_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",this::map,principal.accountId(),size,page*size),page,size,count);
    }
}
