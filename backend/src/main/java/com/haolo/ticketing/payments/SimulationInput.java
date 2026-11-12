package com.haolo.ticketing.payments;

import java.util.*;
import com.haolo.ticketing.bookings.GaBookingRequest;
import com.haolo.ticketing.shared.ApiException;

public record SimulationInput(String scenario,UUID requestId) {
    public SimulationInput {if(scenario==null||requestId==null||!Set.of("SUCCESS","FAILURE","DELAY_TIMEOUT").contains(scenario))throw ApiException.invalid();}
    public static SimulationInput parse(Map<String,Object> body){
        if(body==null||!body.keySet().equals(Set.of("scenario","requestId"))||!(body.get("scenario") instanceof String scenario))throw ApiException.invalid();
        return new SimulationInput(scenario,GaBookingRequest.uuid(body.get("requestId")));
    }
}
