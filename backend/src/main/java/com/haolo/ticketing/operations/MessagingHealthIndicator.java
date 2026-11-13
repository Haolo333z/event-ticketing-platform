package com.haolo.ticketing.operations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("messagingHealthIndicator")
@ConditionalOnProperty(name="messaging.enabled",havingValue="true")
public class MessagingHealthIndicator implements HealthIndicator,AutoCloseable {
    private final Admin admin;
    public MessagingHealthIndicator(@Value("${messaging.bootstrap-servers}")String bootstrap){
        admin=Admin.create(Map.of("bootstrap.servers",bootstrap,"request.timeout.ms",1000,"default.api.timeout.ms",2000));
    }
    @Override public Health health(){
        try{return admin.describeCluster().nodes().get(2,TimeUnit.SECONDS).isEmpty()?Health.down().build():Health.up().build();}
        catch(InterruptedException ex){Thread.currentThread().interrupt();return Health.down().build();}
        catch(Exception ex){return Health.down().build();}
    }
    @Override public void close(){admin.close(Duration.ofSeconds(1));}
}
