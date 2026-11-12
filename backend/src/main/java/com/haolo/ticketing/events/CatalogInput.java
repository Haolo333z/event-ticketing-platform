package com.haolo.ticketing.events;

import java.time.Instant;
import java.util.*;
import com.haolo.ticketing.shared.ApiException;

final class CatalogInput {
    static void fields(Map<String,Object> m,Set<String> allowed,Set<String> required){if(m==null||!allowed.containsAll(m.keySet())||!m.keySet().containsAll(required)||m.values().stream().anyMatch(Objects::isNull))throw ApiException.invalid();}
    static String text(Object v,int max,boolean empty){if(!(v instanceof String s)||(!empty&&s.isBlank())||s.codePointCount(0,s.length())>max)throw ApiException.invalid();return s;}
    static long number(Object v,long max){if(!(v instanceof Byte||v instanceof Short||v instanceof Integer||v instanceof Long)||((Number)v).longValue()<0||((Number)v).longValue()>max)throw ApiException.invalid();return ((Number)v).longValue();}
    static Instant time(Object v){String s=text(v,40,false);if(!s.endsWith("Z"))throw ApiException.invalid();try{return Instant.parse(s);}catch(java.time.DateTimeException ex){throw ApiException.invalid();}}
    static UUID id(Object v){String s=text(v,36,false);UUID id=UUID.fromString(s);if(!id.toString().equalsIgnoreCase(s))throw ApiException.invalid();return id;}
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object v){if(!(v instanceof Map<?,?>))throw ApiException.invalid();return (Map<String,Object>)v;}
    static List<?> list(Object v,int max){if(!(v instanceof List<?> a)||a.isEmpty()||a.size()>max)throw ApiException.invalid();return a;}
    static final Set<String> EVENT=Set.of("title","description","venue","saleStartAt","saleEndAt","startsAt","status","ticketTypes");
    static void eventFields(Map<String,Object> m){
        text(m.get("title"),200,false);text(m.get("description"),2000,true);text(m.get("venue"),200,false);
        var start=time(m.get("saleStartAt"));var end=time(m.get("saleEndAt"));var show=time(m.get("startsAt"));
        if(!start.isBefore(end)||end.isAfter(show)||!Set.of("DRAFT","PUBLISHED","CLOSED").contains(m.get("status")))throw ApiException.invalid();
    }
}
