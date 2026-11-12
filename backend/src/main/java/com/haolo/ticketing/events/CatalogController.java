package com.haolo.ticketing.events;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/events")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service){this.service=service;}
    @GetMapping public CatalogViews.Page list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(page,size);}
    @GetMapping("/{id}") public CatalogViews.Event detail(@PathVariable String id){return service.detail(CatalogInput.id(id));}
    @GetMapping("/{id}/availability") public Map<String,Object> availability(@PathVariable String id){return service.availability(CatalogInput.id(id));}
}
