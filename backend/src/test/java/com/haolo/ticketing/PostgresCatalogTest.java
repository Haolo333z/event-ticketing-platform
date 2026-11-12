package com.haolo.ticketing;

import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=${PG_IT_URL}","expiry.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="PG_IT_URL",matches=".+")
class PostgresCatalogTest {
    @LocalServerPort int port;
    final HttpClient client=HttpClient.newHttpClient();
    final JsonMapper mapper=JsonMapper.builder().build();
    HttpResponse<String> get(String path) throws Exception{return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    @Test void catalogHasBothModesAndAvailability() throws Exception {
        var list=get("/api/events");assertEquals(200,list.statusCode());
        var data=mapper.readTree(list.body());assertEquals(2,data.get("totalElements").asInt());
        assertEquals("SEATED",data.get("items").get(0).get("ticketTypes").get(0).get("mode").asText());
        assertEquals("GA",data.get("items").get(1).get("ticketTypes").get(0).get("mode").asText());
        var seats=get("/api/events/20000000-0000-4000-8000-000000000001/availability");
        assertEquals(4,mapper.readTree(seats.body()).get("ticketTypes").get(0).get("seats").size());
        assertEquals(100,mapper.readTree(get("/api/events/20000000-0000-4000-8000-000000000002/availability").body()).get("ticketTypes").get(0).get("available").asInt());
        assertEquals(200,get("/api/events/20000000-0000-4000-8000-000000000001").statusCode());
    }
    @Test void invalidAndMissingReturnContractErrors() throws Exception {
        for(String path:new String[]{"/api/events?size=101","/api/events?page=-1","/api/events/not-a-uuid","/api/events/1-1-1-1-1","/api/events?size=no"}){
            var r=get(path);assertEquals(400,r.statusCode(),r.body());assertEquals("VALIDATION_ERROR",mapper.readTree(r.body()).get("code").asText());
            assertTrue(r.headers().firstValue("X-Correlation-Id").isPresent());
        }
        assertEquals(404,get("/api/events/99999999-0000-4000-8000-000000000001").statusCode());
    }
}
