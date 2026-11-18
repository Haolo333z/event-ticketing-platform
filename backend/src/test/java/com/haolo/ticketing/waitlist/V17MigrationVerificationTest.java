package com.haolo.ticketing.waitlist;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V17MigrationVerificationTest {
    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

    private static final String URL = "jdbc:postgresql://localhost:5432/ticketing_demo";
    private static final String USER = "ticketing";
    private static final String PASS = "1454158d66ab469493b8bbca1669b0681465533ede684524a1919126f88b3b0b";

    @Test
    void applyAndVerifyV17Migration() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V17 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '17'")) {
                assertTrue(rs.next(), "V17 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V17 must be marked successful");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'event_waitlists'")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString("column_name"));
                assertTrue(cols.containsAll(List.of("id", "event_id", "ticket_type_id", "user_id", "desired_quantity", "status", "notified_at", "trigger_reason")),
                    "event_waitlists must contain required columns, got: " + cols);
            }

            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ticketing'")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 36, "Ticketing schema must contain at least 36 tables after V17");
            }
        }
    }
}
