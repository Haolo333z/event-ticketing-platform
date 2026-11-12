package com.haolo.ticketing.messaging;

import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Strict schema-v1 wire boundary; timestamps are notifications, never payment authority. */
@Component
public class EventCodec {
    private final ObjectMapper json;
    private static final Set<String> ENVELOPE=Set.of("eventId","eventType","schemaVersion","aggregateId","aggregateVersion","occurredAt","correlationId","payload");
    private static final Map<String,Set<String>> FIELDS=Map.of(
        "BookingHeld",Set.of("bookingId","expiresAt"),"PaymentResultRecorded",Set.of("bookingId","paymentResultId"),
        "BookingConfirmed",Set.of("bookingId","confirmedAt"),"BookingPaymentFailed",Set.of("bookingId","failedAt"),
        "BookingCancelled",Set.of("bookingId","cancelledAt"),"BookingExpired",Set.of("bookingId","expiredAt"),
        "BookingRefundRequired",Set.of("bookingId","paymentId","compensationId"));
    public EventCodec(ObjectMapper json){this.json=json;}
    public OutboxWriter.Event decode(String raw,String key){
        if(raw==null||raw.getBytes(StandardCharsets.UTF_8).length>8192)throw new IllegalArgumentException("Invalid event size");
        var n=json.readTree(raw);
        if(!n.isObject()||!names(n).equals(ENVELOPE)||!n.path("schemaVersion").isIntegralNumber()||!n.path("schemaVersion").canConvertToInt()||n.path("schemaVersion").intValue()!=1
            ||!n.path("aggregateVersion").isIntegralNumber()||!n.path("aggregateVersion").canConvertToInt()||n.path("aggregateVersion").longValue()<1)throw new IllegalArgumentException("Unsupported envelope");
        String type=n.path("eventType").asString();var p=n.path("payload");
        if(!FIELDS.containsKey(type)||!p.isObject()||!names(p).equals(FIELDS.get(type)))throw new IllegalArgumentException("Invalid payload");
        UUID id=uuid(n.path("eventId")),aggregate=uuid(n.path("aggregateId")),correlation=uuid(n.path("correlationId"));
        if(!aggregate.toString().equals(key)||!aggregate.equals(uuid(p.path("bookingId"))))throw new IllegalArgumentException("Event key mismatch");
        var payload=new TreeMap<String,Object>();
        for(String name:FIELDS.get(type)){
            var value=p.path(name);if(!value.isString())throw new IllegalArgumentException("String required");
            if(name.endsWith("At"))time(value.asString());else uuid(value);
            payload.put(name,value.asString());
        }
        String occurred=n.path("occurredAt").asString();time(occurred);
        return new OutboxWriter.Event(id,type,1,aggregate,n.path("aggregateVersion").longValue(),occurred,correlation,payload);
    }
    private static Set<String> names(tools.jackson.databind.JsonNode node){var result=new HashSet<String>();node.propertyNames().forEach(result::add);return result;}
    private static UUID uuid(tools.jackson.databind.JsonNode n){if(!n.isString())throw new IllegalArgumentException("UUID required");String s=n.asString();UUID id=UUID.fromString(s);if(!id.toString().equals(s))throw new IllegalArgumentException("Canonical UUID required");return id;}
    private static void time(String s){if(!s.endsWith("Z"))throw new IllegalArgumentException("UTC required");Instant.parse(s);}
    public String encode(OutboxWriter.Event e){return json.writeValueAsString(e);}
    public String hash(OutboxWriter.Event e){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(new OutboxWriter.Event(e.eventId(),e.eventType(),e.schemaVersion(),e.aggregateId(),e.aggregateVersion(),e.occurredAt(),e.correlationId(),new TreeMap<>(e.payload()))).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
