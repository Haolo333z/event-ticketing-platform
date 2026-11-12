package com.haolo.ticketing.events;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.shared.ApiException;
import static com.haolo.ticketing.events.CatalogInput.*;

@Service
public class AdminCatalogService {
    private final CatalogRepository repository;
    private final JdbcTemplate jdbc;
    public AdminCatalogService(CatalogRepository repository,JdbcTemplate jdbc){this.repository=repository;this.jdbc=jdbc;}
    private void timeouts(){jdbc.execute("SET LOCAL lock_timeout='1500ms'");jdbc.execute("SET LOCAL statement_timeout='5s'");}
    @Transactional(timeout=5)
    public CatalogViews.Event create(Map<String,Object> body){
        fields(body,EVENT,EVENT);eventFields(body);var types=list(body.get("ticketTypes"),20);timeouts();UUID eventId=UUID.randomUUID();
        jdbc.update("INSERT INTO ticketing.events(id,title,description,venue,sale_start_at,sale_end_at,starts_at,status) VALUES (?,?,?,?,?,?,?,?)",
            eventId,body.get("title"),body.get("description"),body.get("venue"),Timestamp.from(time(body.get("saleStartAt"))),Timestamp.from(time(body.get("saleEndAt"))),Timestamp.from(time(body.get("startsAt"))),body.get("status"));
        Set<String> labels=new HashSet<>();
        for(Object item:types){
            var t=map(item);String mode=text(t.get("mode"),16,false);
            var keys=mode.equals("GA")?Set.of("name","mode","priceVnd","capacity"):Set.of("name","mode","priceVnd","seats");
            fields(t,keys,keys);if(!Set.of("GA","SEATED").contains(mode))throw ApiException.invalid();
            UUID typeId=UUID.randomUUID();
            jdbc.update("INSERT INTO ticketing.ticket_types(id,event_id,name,mode,price_vnd) VALUES (?,?,?,?,?)",typeId,eventId,text(t.get("name"),100,false),mode,number(t.get("priceVnd"),1000000000000L));
            if(mode.equals("GA"))jdbc.update("INSERT INTO ticketing.inventory_ga(ticket_type_id,capacity) VALUES (?,?)",typeId,number(t.get("capacity"),1000000));
            else for(Object seat:list(t.get("seats"),200)){
                var s=map(seat);fields(s,Set.of("label"),Set.of("label"));String label=text(s.get("label"),40,false);
                if(!labels.add(label)||labels.size()>200)throw ApiException.invalid();
                jdbc.update("INSERT INTO ticketing.seats(id,event_id,ticket_type_id,label) VALUES (?,?,?,?)",UUID.randomUUID(),eventId,typeId,label);
            }
        }
        return repository.find(eventId,false);
    }
    @Transactional(timeout=5)
    public CatalogViews.Event patch(UUID eventId,Map<String,Object> body){
        fields(body,EVENT,Set.of());if(body.isEmpty())throw ApiException.invalid();timeouts();
        if(jdbc.queryForList("SELECT id FROM ticketing.events WHERE id=? FOR UPDATE",eventId).isEmpty())throw ApiException.missing();
        var old=repository.find(eventId,false);var merged=new HashMap<String,Object>();
        merged.put("title",old.title());merged.put("description",old.description());merged.put("venue",old.venue());merged.put("saleStartAt",old.saleStartAt());merged.put("saleEndAt",old.saleEndAt());merged.put("startsAt",old.startsAt());merged.put("status",old.status());merged.putAll(body);eventFields(merged);
        jdbc.update("UPDATE ticketing.events SET title=?,description=?,venue=?,sale_start_at=?,sale_end_at=?,starts_at=?,status=? WHERE id=?",merged.get("title"),merged.get("description"),merged.get("venue"),Timestamp.from(time(merged.get("saleStartAt"))),Timestamp.from(time(merged.get("saleEndAt"))),Timestamp.from(time(merged.get("startsAt"))),merged.get("status"),eventId);
        if(body.containsKey("ticketTypes")){
            Set<UUID> ids=new HashSet<>();
            for(Object item:list(body.get("ticketTypes"),20)){
                var t=map(item);fields(t,Set.of("id","name","priceVnd","active","capacity"),Set.of("id"));UUID typeId=id(t.get("id"));
                if(!ids.add(typeId))throw ApiException.invalid();
                var rows=jdbc.queryForList("SELECT mode FROM ticketing.ticket_types WHERE id=? AND event_id=? FOR UPDATE",typeId,eventId);
                if(rows.isEmpty())throw ApiException.missing();
                if(t.containsKey("capacity")){
                    if(!rows.getFirst().get("mode").equals("GA"))throw ApiException.invalid();long capacity=number(t.get("capacity"),1000000);
                    jdbc.queryForList("SELECT ticket_type_id FROM ticketing.inventory_ga WHERE ticket_type_id=? FOR UPDATE",typeId);
                    if(jdbc.update("UPDATE ticketing.inventory_ga SET capacity=? WHERE ticket_type_id=? AND held_count+sold_count<=?",capacity,typeId,capacity)!=1)throw ApiException.conflict();
                }
                if(t.containsKey("name"))jdbc.update("UPDATE ticketing.ticket_types SET name=? WHERE id=?",text(t.get("name"),100,false),typeId);
                if(t.containsKey("priceVnd"))jdbc.update("UPDATE ticketing.ticket_types SET price_vnd=? WHERE id=?",number(t.get("priceVnd"),1000000000000L),typeId);
                if(t.containsKey("active")){if(!(t.get("active") instanceof Boolean))throw ApiException.invalid();jdbc.update("UPDATE ticketing.ticket_types SET active=? WHERE id=?",t.get("active"),typeId);}
            }
        }
        return repository.find(eventId,false);
    }
}
