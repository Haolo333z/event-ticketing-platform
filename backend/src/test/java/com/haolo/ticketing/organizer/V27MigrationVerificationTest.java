package com.haolo.ticketing.organizer;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V27MigrationVerificationTest {
    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

    private static final String URL = "jdbc:postgresql://localhost:5432/ticketing_demo";
    private static final String USER = "ticketing";
    private static final String PASS = "1454158d66ab469493b8bbca1669b0681465533ede684524a1919126f88b3b0b";

    private static boolean isDbAvailable() {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 5432), 150);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void applyAndVerifyV27Migration() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDbAvailable(), "PostgreSQL is not reachable on localhost:5432");
        System.setProperty("user.timezone", "UTC");
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V27 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Verify V27 recorded in flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '27'")) {
                assertTrue(rs.next(), "V27 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V27 must be marked successful");
            }

            // 2. Verify column user_id exists in ticketing.contact_leads
            try (ResultSet rs = stmt.executeQuery(
                "SELECT column_name, data_type FROM information_schema.columns " +
                "WHERE table_schema = 'ticketing' AND table_name = 'contact_leads' AND column_name = 'user_id'")) {
                assertTrue(rs.next(), "Column user_id must exist in ticketing.contact_leads");
                assertEquals("uuid", rs.getString("data_type").toLowerCase());
            }

            // 3. Verify index on user_id exists
            try (ResultSet rs = stmt.executeQuery(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'ticketing' AND tablename = 'contact_leads' AND indexname = 'idx_contact_leads_user_id'")) {
                assertTrue(rs.next(), "Index idx_contact_leads_user_id must exist");
            }
        }
    }
}
