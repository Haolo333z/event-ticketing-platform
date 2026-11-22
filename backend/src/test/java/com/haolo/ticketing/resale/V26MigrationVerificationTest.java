package com.haolo.ticketing.resale;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V26MigrationVerificationTest {
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
    void applyAndVerifyV26Migration() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDbAvailable(), "PostgreSQL is not reachable on localhost:5432");

        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V26 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '26'")) {
                assertTrue(rs.next(), "V26 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V26 must be successful");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM ticketing.resale_listings")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 0);
            }
        }
    }
}
