package com.haolo.ticketing.shared;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String id=req.getHeader("X-Correlation-Id");
        try {if(id==null||!UUID.fromString(id).toString().equalsIgnoreCase(id))throw new IllegalArgumentException();}
        catch(IllegalArgumentException ex){id=UUID.randomUUID().toString();}
        req.setAttribute("correlationId",id);res.setHeader("X-Correlation-Id",id);chain.doFilter(req,res);
    }
}
