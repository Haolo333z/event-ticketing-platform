package com.haolo.ticketing;

import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.context.ActiveProfiles;
import com.haolo.ticketing.operations.DatabaseHealthIndicator;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.flyway.enabled=false","demo.seed.enabled=false","demo.auth.enabled=false","expiry.enabled=false","messaging.enabled=false"})
@Import(HealthEndpointTest.FaultConfiguration.class) @ExtendWith(OutputCaptureExtension.class) @ActiveProfiles("test")
class HealthEndpointTest {
    @LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired FaultDataSource source;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext context;
    static final String MARKER="health-private-marker";
    static class FaultDataSource extends AbstractDataSource {
        final AtomicBoolean broken=new AtomicBoolean();
        @Override public Connection getConnection()throws SQLException{if(broken.get())throw new SQLException(MARKER);return DriverManager.getConnection("jdbc:h2:mem:healthprobe;DB_CLOSE_DELAY=-1","sa","");}
        @Override public Connection getConnection(String user,String password)throws SQLException{return getConnection();}
    }
    @TestConfiguration static class FaultConfiguration {@Bean FaultDataSource dataSource(){return new FaultDataSource();}}
    void health(String path,int status,String expected)throws Exception{
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health"+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(status,response.statusCode(),response.body());var body=new ObjectMapper().readTree(response.body());
        assertEquals(expected,body.path("status").asString());assertFalse(body.has("components"));assertFalse(body.has("details"));assertFalse(response.body().contains(MARKER));
        assertTrue(Set.of("status","groups").containsAll(body.propertyNames()));
    }
    @Test void databaseFailureChangesReadinessButNotLivenessAndRecoversWithoutBroker(CapturedOutput output)throws Exception{
        assertFalse(context.containsBean("messagingHealthIndicator"));
        health("",200,"UP");health("/readiness",200,"UP");health("/liveness",200,"UP");
        source.broken.set(true);
        try{health("",503,"DOWN");health("/readiness",503,"DOWN");health("/liveness",200,"UP");}
        finally{source.broken.set(false);}
        health("",200,"UP");health("/readiness",200,"UP");assertFalse(output.getAll().contains(MARKER));
        assertFalse(output.getAll().contains("demo-customer-token"));assertFalse(output.getAll().contains("demo-admin-token"));
    }
    @Test void failedValidationAlsoReportsDownWithoutLeakingDetails()throws Exception{
        var data=mock(DataSource.class);var connection=mock(Connection.class);when(data.getConnection()).thenReturn(connection);
        var health=new DatabaseHealthIndicator(data);when(connection.isValid(1)).thenReturn(false);
        assertEquals("DOWN",health.health().getStatus().getCode());verify(connection).close();
        when(connection.isValid(1)).thenThrow(new SQLException(MARKER));assertTrue(health.health().getDetails().isEmpty());
        assertEquals("DOWN",health.health().getStatus().getCode());
    }
}
