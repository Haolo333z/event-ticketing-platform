package com.haolo.ticketing.operations;

import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;

/** Scope every thread boundary; UUID metadata only, never headers or event payloads. */
public final class TraceContext implements AutoCloseable {
    private final Map<String,String> previous=MDC.getCopyOfContextMap();
    private TraceContext(UUID correlation,UUID booking,UUID event){
        put("correlationId",correlation);put("bookingId",booking);put("eventId",event);
    }
    private static void put(String key,UUID value){if(value==null)MDC.remove(key);else MDC.put(key,value.toString());}
    public static TraceContext open(UUID correlation,UUID booking,UUID event){return new TraceContext(correlation,booking,event);}
    @Override public void close(){if(previous==null)MDC.clear();else MDC.setContextMap(previous);}
}
