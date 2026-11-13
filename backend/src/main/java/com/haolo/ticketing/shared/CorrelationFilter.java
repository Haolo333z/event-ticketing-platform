package com.haolo.ticketing.shared;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import com.haolo.ticketing.operations.TraceContext;
import com.haolo.ticketing.operations.TraceLog;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String id=req.getHeader("X-Correlation-Id");
        try {if(id==null||!UUID.fromString(id).toString().equalsIgnoreCase(id))throw new IllegalArgumentException();}
        catch(IllegalArgumentException ex){id=UUID.randomUUID().toString();}
        UUID correlation=UUID.fromString(id);
        req.setAttribute("correlationId",id);res.setHeader("X-Correlation-Id",id);
        try(var context=TraceContext.open(correlation,null,null)){
            boolean completed=false;
            try{chain.doFilter(req,res);completed=true;}
            finally{
                // Never log raw URI/query/header/body, including when routing or authentication failed.
                Object route=req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                UUID booking=req.getAttribute("traceBookingId") instanceof UUID value?value:null;
                UUID event=req.getAttribute("traceEventId") instanceof UUID value?value:null;
                if(booking==null&&route!=null&&route.toString().startsWith("/api/bookings/{id}")){
                    Object variables=req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                    if(variables instanceof java.util.Map<?,?> paths&&paths.get("id") instanceof String value){
                        try{UUID parsed=UUID.fromString(value);if(parsed.toString().equalsIgnoreCase(value))booking=parsed;}catch(IllegalArgumentException ignored){}
                    }
                }
                try(var responseContext=TraceContext.open(correlation,booking,event)){
                    String method=java.util.Set.of("GET","POST","PATCH","PUT","DELETE","OPTIONS","HEAD").contains(req.getMethod())?req.getMethod():"OTHER";
                    TraceLog.http(method,route==null?"UNMATCHED":route.toString(),completed?res.getStatus():500);
                }
            }
        }
    }
}
