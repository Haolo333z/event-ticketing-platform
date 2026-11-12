package com.haolo.ticketing.bookings;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.haolo.ticketing.shared.ApiException;

public record SeatBookingRequest(UUID eventId,UUID ticketTypeId,List<UUID> seatIds) implements BookingRequest {
    public SeatBookingRequest {
        if(eventId==null||ticketTypeId==null||seatIds==null||seatIds.isEmpty()||seatIds.size()>6||seatIds.stream().anyMatch(Objects::isNull)||new HashSet<>(seatIds).size()!=seatIds.size())throw ApiException.invalid();
        seatIds=seatIds.stream().sorted(Comparator.comparing(UUID::toString)).toList();
    }
    public int quantity(){return seatIds.size();}
    public static SeatBookingRequest parse(Map<String,Object> body){
        if(body==null||!body.keySet().equals(Set.of("eventId","ticketTypeId","seatIds"))||!(body.get("seatIds") instanceof List<?> ids))throw ApiException.invalid();
        return new SeatBookingRequest(GaBookingRequest.uuid(body.get("eventId")),GaBookingRequest.uuid(body.get("ticketTypeId")),ids.stream().map(GaBookingRequest::uuid).toList());
    }
    public String hash(){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("SEATED:"+eventId+":"+ticketTypeId+":"+seatIds).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
