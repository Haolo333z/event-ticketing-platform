package com.haolo.ticketing.events;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class CatalogBookingAccess {
    private final JdbcTemplate jdbc;
    public CatalogBookingAccess(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public long lockGaPrice(UUID eventId,UUID typeId){
        var events=jdbc.queryForList("SELECT * FROM ticketing.events WHERE id=? FOR SHARE",eventId);
        if(events.isEmpty()||!events.getFirst().get("status").equals("PUBLISHED"))throw ApiException.missing();
        var types=jdbc.queryForList("SELECT * FROM ticketing.ticket_types WHERE id=? AND event_id=? FOR SHARE",typeId,eventId);
        if(types.isEmpty())throw ApiException.missing();
        var type=types.getFirst();
        if(!type.get("mode").equals("GA"))throw new ApiException(400,"VALIDATION_ERROR","Phần này chỉ hỗ trợ vé GA");
        boolean onSale=jdbc.queryForObject("SELECT active AND e.status='PUBLISHED' AND clock_timestamp()>=e.sale_start_at AND clock_timestamp()<e.sale_end_at FROM ticketing.events e JOIN ticketing.ticket_types t ON t.event_id=e.id WHERE t.id=?",Boolean.class,typeId);
        if(!onSale)throw new ApiException(409,"INVENTORY_UNAVAILABLE","Sự kiện chưa mở bán hoặc loại vé đã ngừng bán");
        return ((Number)type.get("price_vnd")).longValue();
    }
}
