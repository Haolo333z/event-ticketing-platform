package com.haolo.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.profiles.active=prod",
    "spring.datasource.url=jdbc:h2:mem:scaffold",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "demo.seed.enabled=false",
    "expiry.enabled=false","messaging.enabled=false",
    "demo.auth.enabled=false"
})
class TicketingApplicationTest {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext context;
    @Test
    void contextLoadsWithoutExternalServices() {
        // Isolated context check only; real PostgreSQL/Flyway checks use verify-local.ps1.
        var resolver=context.getBean(org.springframework.boot.actuate.endpoint.EndpointAccessResolver.class);
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.boot.actuate.endpoint.Access.NONE,
            resolver.accessFor(org.springframework.boot.actuate.endpoint.EndpointId.of("metrics"),org.springframework.boot.actuate.endpoint.Access.READ_ONLY),"Metrics must have no access outside local/test even if its backing bean exists");
    }
}
