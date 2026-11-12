package com.haolo.ticketing;

import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationContext;
import com.haolo.ticketing.identity.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=${PG_IT_URL}","expiry.enabled=false"})
@ActiveProfiles("prod") @EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+")
class PostgresProductionProfileTest {
    @LocalServerPort int port;
    @Autowired DemoSettings settings;
    @Autowired ApplicationContext context;
    @Test void productionHasNoSeedRunnerOrWorkingMockTokens() throws Exception {
        assertFalse(settings.enabled);assertFalse(settings.safeProfile);
        assertTrue(settings.customerToken.isBlank());assertTrue(settings.adminToken.isBlank());
        assertTrue(context.getBeansOfType(DemoSeedRunner.class).isEmpty());
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/events"))
            .header("Authorization","Bearer demo-admin-token").GET().build();
        assertEquals(401,HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
