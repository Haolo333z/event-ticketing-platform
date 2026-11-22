package com.haolo.ticketing.payments;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V19MigrationVerificationTest {
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
    void applyAndVerifyV19Migration() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDbAvailable(), "PostgreSQL is not reachable on localhost:5432");
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V19 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '19'")) {
                assertTrue(rs.next(), "V19 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V19 must be marked successful");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'reconciliation_runs'")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString("column_name"));
                assertTrue(cols.containsAll(List.of("id", "reconciliation_date", "provider", "total_transactions", "matched_count", "discrepancy_count", "status")),
                    "reconciliation_runs must contain required columns, got: " + cols);
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'reconciliation_discrepancies'")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString("column_name"));
                assertTrue(cols.containsAll(List.of("id", "run_id", "merchant_ref", "discrepancy_type", "system_amount_vnd", "gateway_amount_vnd", "status")),
                    "reconciliation_discrepancies must contain required columns, got: " + cols);
            }
        }
    }
}
