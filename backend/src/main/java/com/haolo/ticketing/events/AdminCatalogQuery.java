package com.haolo.ticketing.events;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

/** Private catalog queries; never weaken the public PUBLISHED filter. */
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class AdminCatalogQuery {
    private final CatalogRepository repository;private final JdbcTemplate jdbc;
    public AdminCatalogQuery(CatalogRepository repository,JdbcTemplate jdbc){this.repository=repository;this.jdbc=jdbc;}
    private void admin(DemoPrincipal principal){if(principal==null||!principal.role().equals("ADMIN"))throw new ApiException(403,"FORBIDDEN","Chỉ quản trị viên được xem dữ liệu này");}
    public CatalogViews.Page list(DemoPrincipal principal,int page,int size){
        admin(principal);if(page<0||page>10000||size<1||size>100)throw ApiException.invalid();
        long total=jdbc.queryForObject("SELECT count(*) FROM ticketing.events",Long.class);
        return new CatalogViews.Page(jdbc.query("SELECT * FROM ticketing.events ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",repository::map,size,page*size),page,size,total);
    }
    public Map<String,Object> inventory(DemoPrincipal principal,UUID id){
        admin(principal);var event=repository.find(id,false);var types=new ArrayList<Map<String,Object>>();
        for(var type:event.ticketTypes()){
            if(type.mode().equals("GA"))types.add(jdbc.queryForObject("SELECT capacity,held_count,sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",(r,n)->Map.of("ticketTypeId",type.id(),"mode","GA","capacity",r.getInt(1),"held",r.getInt(2),"sold",r.getInt(3),"available",r.getInt(1)-r.getInt(2)-r.getInt(3)),type.id()));
            else types.add(Map.of("ticketTypeId",type.id(),"mode","SEATED","seats",jdbc.query("SELECT id,label,status FROM ticketing.seats WHERE ticket_type_id=? ORDER BY label,id",(r,n)->Map.of("id",r.getObject("id",UUID.class),"label",r.getString("label"),"status",r.getString("status")),type.id())));
        }
        String now=jdbc.queryForObject("SELECT clock_timestamp()",(r,n)->r.getTimestamp(1).toInstant().toString());
        return Map.of("eventId",id,"asOf",now,"ticketTypes",types);
    }
}
