package com.haolo.ticketing.events;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/events")
public class AdminCatalogController {
    private final AdminCatalogService service;
    public AdminCatalogController(AdminCatalogService service){this.service=service;}
    @PostMapping public ResponseEntity<CatalogViews.Event> create(@RequestBody Map<String,Object> body){return ResponseEntity.status(201).body(service.create(body));}
    @PatchMapping("/{id}") public CatalogViews.Event patch(@PathVariable String id,@RequestBody Map<String,Object> body){return service.patch(CatalogInput.id(id),body);}
}
