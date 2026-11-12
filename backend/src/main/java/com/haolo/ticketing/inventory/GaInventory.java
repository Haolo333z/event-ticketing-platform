package com.haolo.ticketing.inventory;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class GaInventory {
    private final JdbcTemplate jdbc;
    public GaInventory(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void reserve(UUID typeId,int quantity){
        if(quantity<1||quantity>6)throw ApiException.invalid();
        int changed=jdbc.update("UPDATE ticketing.inventory_ga SET held_count=held_count+? WHERE ticket_type_id=? AND held_count+sold_count+?<=capacity",quantity,typeId,quantity);
        if(changed!=1)throw new ApiException(409,"INVENTORY_UNAVAILABLE","Không còn đủ vé cho số lượng đã chọn");
    }
}
