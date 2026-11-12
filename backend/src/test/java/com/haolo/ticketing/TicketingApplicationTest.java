package com.haolo.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TicketingApplicationTest {
    @Test
    void contextLoadsWithoutExternalServices() {
        // Scaffold must build/test before PostgreSQL and Kafka are introduced.
    }
}
