package com.haolo.ticketing;

import com.haolo.ticketing.identity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.datasource.url=${PG_IT_URL}","expiry.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+")
class PostgresSeedTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoSeedService seed;
    @Test void seedIsIdempotentAndLocalOnly(){
        seed.seed();seed.seed();
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.users",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticketing.events WHERE id IN ('20000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000002')",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM ticketing.seats WHERE event_id='20000000-0000-4000-8000-000000000001'",Integer.class));
        assertEquals(100,jdbc.queryForObject("SELECT capacity FROM ticketing.inventory_ga WHERE ticket_type_id='30000000-0000-4000-8000-000000000002'",Integer.class));
        var env=new MockEnvironment();env.setActiveProfiles("prod");
        assertFalse(new DemoSettings(env,false,"","").enabled);
        assertThrows(IllegalStateException.class,()->new DemoSettings(env,true,"customer","admin"));
        env.setActiveProfiles("local","prod");
        assertThrows(IllegalStateException.class,()->new DemoSettings(env,true,"customer","admin"));
    }
}
