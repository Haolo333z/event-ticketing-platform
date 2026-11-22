package com.haolo.ticketing.identity;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V28MigrationVerificationTest {
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
    void applyAndVerifyV28Migration() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDbAvailable(), "PostgreSQL is not reachable on localhost:5432");
        System.setProperty("user.timezone", "UTC");
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V28 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Verify V28 recorded in flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '28'")) {
                assertTrue(rs.next(), "V28 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V28 must be marked successful");
            }

            // 2. Verify avatar_url and VAT columns in ticketing.users
            try (ResultSet rs = stmt.executeQuery(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = 'ticketing' AND table_name = 'users' AND column_name IN ('avatar_url', 'vat_company_name', 'vat_tax_code', 'vat_company_address', 'vat_recipient_email')")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                assertEquals(5, count, "All 5 new columns must exist in ticketing.users");
            }

            // 3. Verify user_agent and ip_address in ticketing.refresh_tokens
            try (ResultSet rs = stmt.executeQuery(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = 'ticketing' AND table_name = 'refresh_tokens' AND column_name IN ('user_agent', 'ip_address')")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                assertEquals(2, count, "user_agent and ip_address must exist in ticketing.refresh_tokens");
            }

            // 4. Verify index on refresh_tokens
            try (ResultSet rs = stmt.executeQuery(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'ticketing' AND tablename = 'refresh_tokens' AND indexname = 'idx_refresh_tokens_user_active'")) {
                assertTrue(rs.next(), "Index idx_refresh_tokens_user_active must exist");
            }
        }
    }
}
