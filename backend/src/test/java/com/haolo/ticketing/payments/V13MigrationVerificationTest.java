package com.haolo.ticketing.payments;

import java.sql.*;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V13MigrationVerificationTest {
    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

    private static final String URL = "jdbc:postgresql://localhost:5432/ticketing_demo";
    private static final String USER = "ticketing";
    private static final String PASS = "1454158d66ab469493b8bbca1669b0681465533ede684524a1919126f88b3b0b";

    @Test
    void applyAndVerifyV13Migration() throws Exception {
        System.setProperty("user.timezone", "UTC");
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V13 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Verify V13 recorded in flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '13'")) {
                assertTrue(rs.next(), "V13 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V13 must be marked successful");
            }

            // 2. Verify payment_attempts table exists
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'payment_attempts'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.containsAll(List.of("id", "booking_id", "provider", "merchant_ref", "amount_vnd", "status", "gateway_url")),
                    "payment_attempts must contain required columns, got: " + cols);
            }

            // 3. Verify payment_notifications table exists
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'payment_notifications'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.containsAll(List.of("id", "attempt_id", "provider", "provider_tx_ref", "event_type", "raw_payload", "signature_valid")),
                    "payment_notifications must contain required columns, got: " + cols);
            }

            // 4. Verify refund_attempts table exists
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'refund_attempts'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.containsAll(List.of("id", "booking_id", "payment_attempt_id", "amount_vnd", "reason", "status")),
                    "refund_attempts must contain required columns, got: " + cols);
            }

            // 5. Verify total tables in ticketing schema is 32
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ticketing'")) {
                assertTrue(rs.next());
                assertEquals(32, rs.getInt(1), "Ticketing schema must contain exactly 32 tables after V13");
            }
        }
    }
}
