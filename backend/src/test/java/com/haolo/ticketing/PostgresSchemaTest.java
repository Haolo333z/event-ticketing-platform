package com.haolo.ticketing;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PG_IT_URL", matches=".+")
class PostgresSchemaTest {
    @Test void migrateAndCheckConstraintsOnPostgres() throws Exception {
        var flyway=Flyway.configure().dataSource(System.getenv("PG_IT_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"))
            .defaultSchema("public").locations("classpath:db/migration").load();
        flyway.migrate();
        assertEquals(0,flyway.migrate().migrationsExecuted);
        try(var c=DriverManager.getConnection(System.getenv("PG_IT_URL"),System.getenv("POSTGRES_USER"),System.getenv("POSTGRES_PASSWORD"));var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT count(*) FROM public.flyway_schema_history WHERE version='2' AND success")){r.next();assertEquals(1,r.getInt(1));}
            assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO ticketing.users VALUES ('"+UUID.randomUUID()+"','ROOT','invalid',now())"));
            assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO ticketing.inventory_ga VALUES ('"+UUID.randomUUID()+"',10,0,0)"));
            try(var r=s.executeQuery("SELECT count(*) FROM pg_indexes WHERE schemaname='ticketing'")){r.next();assertTrue(r.getInt(1)>=20);}
            // Each attempt is rolled back to preserve the test database for later task gates.
            c.setAutoCommit(false);
            UUID e=UUID.randomUUID(),t=UUID.randomUUID();
            s.executeUpdate("INSERT INTO ticketing.events VALUES ('"+e+"','test','','venue',now(),now()+interval '1h',now()+interval '2h','DRAFT',now())");
            s.executeUpdate("INSERT INTO ticketing.ticket_types VALUES ('"+t+"','"+e+"','GA','GA',100,true)");
            s.executeUpdate("INSERT INTO ticketing.inventory_ga VALUES ('"+t+"',10,1,2)");
            var save=c.setSavepoint();
            assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO ticketing.ticket_types VALUES ('"+t+"','"+e+"','duplicate','GA',100,true)"));
            c.rollback(save);
            UUID wrongEvent=UUID.randomUUID();
            s.executeUpdate("INSERT INTO ticketing.events VALUES ('"+wrongEvent+"','other','','venue',now(),now()+interval '1h',now()+interval '2h','DRAFT',now())");
            save=c.setSavepoint();
            assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO ticketing.seats(id,event_id,ticket_type_id,label) VALUES ('"+UUID.randomUUID()+"','"+wrongEvent+"','"+t+"','wrong event')"));
            c.rollback(save);
            save=c.setSavepoint();
            assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO ticketing.seats(id,event_id,ticket_type_id,label,status) VALUES ('"+UUID.randomUUID()+"','"+e+"','"+t+"','unowned','HELD')"));
            c.rollback(save);
            assertThrows(SQLException.class,()->s.executeUpdate("UPDATE ticketing.inventory_ga SET capacity=2 WHERE ticket_type_id='"+t+"'"));
            c.rollback();
            c.setAutoCommit(true);
            assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO ticketing.ticket_types VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','bad','GA',-1,true)"));
        }
    }
}
