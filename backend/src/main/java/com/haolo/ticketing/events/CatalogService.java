package com.haolo.ticketing.events;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CatalogService {
    private final CatalogRepository repository;
    public CatalogService(CatalogRepository repository){this.repository=repository;}
    public CatalogViews.Page list(int page,int size){if(page<0||page>10000||size<1||size>100)throw ApiException.invalid();return repository.list(page,size);}
    public CatalogViews.Event detail(UUID id){return repository.find(id,true);}
    public Map<String,Object> availability(UUID id){return repository.availability(id);}
}
