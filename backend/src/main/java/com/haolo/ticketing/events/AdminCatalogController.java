package com.haolo.ticketing.events;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController @RequestMapping("/api/admin/events")
public class AdminCatalogController {
    private final AdminCatalogService service;
    private final AdminCatalogQuery query;
    public AdminCatalogController(AdminCatalogService service,AdminCatalogQuery query){this.service=service;this.query=query;}
    @GetMapping public CatalogViews.Page list(@AuthenticationPrincipal DemoPrincipal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return query.list(principal,page,size);}
    @GetMapping("/{id}/inventory") public Map<String,Object> inventory(@AuthenticationPrincipal DemoPrincipal principal,@PathVariable String id){return query.inventory(principal,CatalogInput.id(id));}
    @PostMapping public ResponseEntity<CatalogViews.Event> create(@RequestBody Map<String,Object> body){return ResponseEntity.status(201).body(service.create(body));}
    @PatchMapping("/{id}") public CatalogViews.Event patch(@PathVariable String id,@RequestBody Map<String,Object> body){return service.patch(CatalogInput.id(id),body);}
}
