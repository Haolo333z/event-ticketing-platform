package com.haolo.ticketing.checkin;

import java.sql.*;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V14MigrationVerificationTest {
    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

    private static final String URL = "jdbc:postgresql://localhost:5432/ticketing_demo";
    private static final String USER = "ticketing";
    private static final String PASS = "1454158d66ab469493b8bbca1669b0681465533ede684524a1919126f88b3b0b";

    @Test
    void applyAndVerifyV14Migration() throws Exception {
        System.setProperty("user.timezone", "UTC");
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V14 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Verify V14 recorded in flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '14'")) {
                assertTrue(rs.next(), "V14 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V14 must be marked successful");
            }

            // 2. Verify ticket_check_ins table exists with required columns
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'ticket_check_ins'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.containsAll(List.of("id", "ticket_id", "event_id", "staff_user_id", "gate_name", "device_info", "checked_in_at")),
                    "ticket_check_ins must contain required columns, got: " + cols);
            }

            // 3. Verify total tables in ticketing schema is at least 33
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ticketing'")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 33, "Ticketing schema must contain at least 33 tables after V14");
            }
        }
    }
}
