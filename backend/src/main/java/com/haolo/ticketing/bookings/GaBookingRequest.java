package com.haolo.ticketing.bookings;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.haolo.ticketing.shared.ApiException;

public record GaBookingRequest(UUID eventId,UUID ticketTypeId,int quantity) {
    public GaBookingRequest {if(eventId==null||ticketTypeId==null||quantity<1||quantity>6)throw ApiException.invalid();}
    public static GaBookingRequest parse(Map<String,Object> body){
        if(body==null||!body.keySet().equals(Set.of("eventId","ticketTypeId","quantity")))throw ApiException.invalid();
        Object q=body.get("quantity");
        if(!(q instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<1||n.doubleValue()>6||n.doubleValue()!=Math.rint(n.doubleValue()))throw ApiException.invalid();
        return new GaBookingRequest(uuid(body.get("eventId")),uuid(body.get("ticketTypeId")),((Number)q).intValue());
    }
    public static UUID uuid(Object value){
        if(!(value instanceof String text))throw ApiException.invalid();
        try {UUID id=UUID.fromString(text);if(!id.toString().equalsIgnoreCase(text))throw ApiException.invalid();return id;}
        catch(IllegalArgumentException ex){throw ApiException.invalid();}
    }
    public String hash(){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((eventId+":"+ticketTypeId+":"+quantity).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
