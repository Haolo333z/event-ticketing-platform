package com.haolo.ticketing.events;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.haolo.ticketing.shared.ApiException;

@Repository
public class CatalogRepository {
    final JdbcTemplate jdbc;
    public CatalogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    CatalogViews.Event map(ResultSet r,int row) throws SQLException {
        UUID id=r.getObject("id",UUID.class);
        return new CatalogViews.Event(id,r.getString("title"),r.getString("description"),r.getString("venue"),
            r.getTimestamp("sale_start_at").toInstant().toString(),r.getTimestamp("sale_end_at").toInstant().toString(),r.getTimestamp("starts_at").toInstant().toString(),r.getString("status"),types(id));
    }
    List<CatalogViews.TicketType> types(UUID id){return jdbc.query("SELECT * FROM ticketing.ticket_types WHERE event_id=? ORDER BY id",
        (r,n)->new CatalogViews.TicketType(r.getObject("id",UUID.class),r.getString("name"),r.getString("mode"),r.getLong("price_vnd"),r.getBoolean("active")),id);}
    public CatalogViews.Event find(UUID id,boolean published){
        var rows=jdbc.query("SELECT * FROM ticketing.events WHERE id=?"+(published?" AND status='PUBLISHED'":""),this::map,id);
        if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    public CatalogViews.Page list(int page,int size){
        long total=jdbc.queryForObject("SELECT count(*) FROM ticketing.events WHERE status='PUBLISHED'",Long.class);
        return new CatalogViews.Page(jdbc.query("SELECT * FROM ticketing.events WHERE status='PUBLISHED' ORDER BY starts_at,id LIMIT ? OFFSET ?",this::map,size,page*size),page,size,total);
    }
    public Map<String,Object> availability(UUID id){
        var event=find(id,true);var types=new ArrayList<Map<String,Object>>();
        for(var t:event.ticketTypes())if(t.active()){
            if(t.mode().equals("GA"))types.add(Map.of("ticketTypeId",t.id(),"mode","GA","available",jdbc.queryForObject("SELECT capacity-held_count-sold_count FROM ticketing.inventory_ga WHERE ticket_type_id=?",Integer.class,t.id())));
            else types.add(Map.of("ticketTypeId",t.id(),"mode","SEATED","seats",jdbc.query("SELECT id,label,status FROM ticketing.seats WHERE ticket_type_id=? ORDER BY id",(r,n)->Map.of("id",r.getObject("id",UUID.class),"label",r.getString("label"),"status",r.getString("status")),t.id())));
        }
        String now=jdbc.queryForObject("SELECT clock_timestamp()",(r,n)->r.getTimestamp(1).toInstant().toString());
        return Map.of("eventId",id,"asOf",now,"ticketTypes",types);
    }
}
