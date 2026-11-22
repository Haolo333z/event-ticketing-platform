package com.haolo.ticketing.operations;

import java.sql.*;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V15MigrationVerificationTest {
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
    void applyAndVerifyV15Migration() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDbAvailable(), "PostgreSQL is not reachable on localhost:5432");
        System.setProperty("user.timezone", "UTC");
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        flyway.repair();
        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V15 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Verify V15 recorded in flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '15'")) {
                assertTrue(rs.next(), "V15 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V15 must be marked successful");
            }

            // 2. Verify contact_leads table has updated columns
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'contact_leads'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.containsAll(List.of("id", "full_name", "email", "phone", "status", "notes", "updated_at")),
                    "contact_leads must contain updated columns, got: " + cols);
            }

            // 2b. Verify users table has status column
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'users'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.contains("status"), "users must contain status column");
            }

            // 3. Verify email_notifications table exists with required columns
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'email_notifications'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.containsAll(List.of("id", "booking_id", "recipient_email", "subject", "template_type", "payload_json", "status", "sent_at")),
                    "email_notifications must contain required columns, got: " + cols);
            }

            // 4. Verify audit_logs table exists
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'audit_logs'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.containsAll(List.of("id", "actor_id", "actor_role", "action", "target_type", "target_id", "payload_diff")),
                    "audit_logs must contain required columns, got: " + cols);
            }

            // 5. Verify total tables in ticketing schema is at least 35
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ticketing'")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 35, "Ticketing schema must contain at least 35 tables after V15");
            }
        }
    }
}
