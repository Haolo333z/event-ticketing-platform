package com.haolo.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.datasource.url=jdbc:h2:mem:scaffold",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false"
})
class TicketingApplicationTest {
    @Test
    void contextLoadsWithoutExternalServices() {
        // Isolated context check only; real PostgreSQL/Flyway checks use verify-local.ps1.
    }
}
