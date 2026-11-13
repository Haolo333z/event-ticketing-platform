package com.haolo.ticketing.operations;

import javax.sql.DataSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Replaces the default db probe to avoid logging JDBC error messages or connection details. */
@Component("dbHealthIndicator")
public class DatabaseHealthIndicator implements HealthIndicator {
    private final DataSource source;
    public DatabaseHealthIndicator(DataSource source){this.source=source;}
    @Override public Health health(){
        try(var connection=source.getConnection()){return connection.isValid(1)?Health.up().build():Health.down().build();}
        catch(Exception ex){return Health.down().build();}
    }
}
