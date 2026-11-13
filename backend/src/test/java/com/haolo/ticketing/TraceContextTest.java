package com.haolo.ticketing;

import java.util.*;
import java.util.concurrent.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import com.haolo.ticketing.operations.*;
import com.haolo.ticketing.shared.CorrelationFilter;
import static org.junit.jupiter.api.Assertions.*;

class TraceContextTest {
    @Test void nestedScopesRestoreContextEvenOnFailureAndThreadReuse()throws Exception{
        try(var executor=Executors.newSingleThreadExecutor()){
            for(int i=0;i<12;i++)executor.submit(()->{
                assertNull(MDC.get("correlationId"));UUID correlation=UUID.randomUUID(),booking=UUID.randomUUID(),event=UUID.randomUUID();
                try(var outer=TraceContext.open(correlation,null,null)){
                    assertThrows(IllegalStateException.class,()->{try(var inner=TraceContext.open(UUID.randomUUID(),booking,event)){throw new IllegalStateException();}});
                    assertEquals(correlation.toString(),MDC.get("correlationId"));assertNull(MDC.get("bookingId"));assertNull(MDC.get("eventId"));
                }
                assertTrue(MDC.getCopyOfContextMap()==null||MDC.getCopyOfContextMap().isEmpty());return null;
            }).get();
        }
    }
    @Test void httpLogsOnlySafeMetadataAndDoesNotLeakIdsToNextRequest()throws Exception{
        var logger=(Logger)LoggerFactory.getLogger(TraceLog.class);
        var appender=new ListAppender<ILoggingEvent>(){
            @Override protected void append(ILoggingEvent event){event.prepareForDeferredProcessing();super.append(event);}
        };appender.start();logger.addAppender(appender);
        UUID correlation=UUID.randomUUID(),booking=UUID.randomUUID(),event=UUID.randomUUID();
        String secret="private-marker-"+UUID.randomUUID();
        try{
            var req=new MockHttpServletRequest("POST","/api/bookings");req.addHeader("X-Correlation-Id",correlation.toString());req.addHeader("Authorization","Bearer "+secret);req.setQueryString("payment="+secret);req.setContent(secret.getBytes());
            var response=new MockHttpServletResponse();
            new CorrelationFilter().doFilter(req,response,(r,s)->{
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/bookings");
                req.setAttribute("traceBookingId",booking);req.setAttribute("traceEventId",event);response.setStatus(202);
            });
            assertEquals(correlation.toString(),response.getHeader("X-Correlation-Id"));
            var second=new MockHttpServletRequest("GET","/unknown/"+secret);second.addHeader("X-Correlation-Id",secret);
            var secondResponse=new MockHttpServletResponse();new CorrelationFilter().doFilter(second,secondResponse,(r,s)->{});
            assertEquals(2,appender.list.size());var first=appender.list.get(0);var next=appender.list.get(1);
            assertEquals(Map.of("correlationId",correlation.toString(),"bookingId",booking.toString(),"eventId",event.toString()),first.getMDCPropertyMap());
            assertEquals(Set.of("correlationId"),next.getMDCPropertyMap().keySet());
            assertDoesNotThrow(()->UUID.fromString(next.getMDCPropertyMap().get("correlationId")));
            for(var entry:appender.list){assertFalse(entry.getFormattedMessage().contains(secret));assertFalse(entry.getKeyValuePairs().toString().contains(secret));}
            assertNull(MDC.get("correlationId"));assertNull(MDC.get("bookingId"));assertNull(MDC.get("eventId"));
            assertThrows(jakarta.servlet.ServletException.class,()->new CorrelationFilter().doFilter(new MockHttpServletRequest("GET","/failed"),new MockHttpServletResponse(),(r,s)->{throw new jakarta.servlet.ServletException(secret);}));
            assertNull(MDC.get("correlationId"));assertEquals(500,appender.list.get(2).getKeyValuePairs().stream().filter(p->p.key.equals("status")).findFirst().orElseThrow().value);
        }finally{logger.detachAppender(appender);appender.stop();}
    }
}
