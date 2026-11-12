package com.haolo.ticketing.events;

import java.util.*;
public final class CatalogViews {
    private CatalogViews(){}
    public record TicketType(UUID id,String name,String mode,long priceVnd,boolean active){}
    public record Event(UUID id,String title,String description,String venue,String saleStartAt,String saleEndAt,String startsAt,String status,List<TicketType> ticketTypes){}
    public record Page(List<Event> items,int page,int size,long totalElements){}
}
