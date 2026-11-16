package com.haolo.ticketing.identity;

import java.sql.*;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V11MigrationVerificationTest {
    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

    private static final String URL = "jdbc:postgresql://localhost:5432/ticketing_demo";
    private static final String USER = "ticketing";
    private static final String PASS = "1454158d66ab469493b8bbca1669b0681465533ede684524a1919126f88b3b0b";

    @Test
    void applyAndVerifyV11Migration() throws Exception {
        System.setProperty("user.timezone", "UTC");
        // Run Flyway migrate
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration should succeed");

        // Verify with JDBC directly
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Verify V11 recorded in flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '11'")) {
                assertTrue(rs.next(), "V11 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V11 must be successful");
            }

            // 2. Verify roles table seeded with 4 roles
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM ticketing.roles ORDER BY id")) {
                List<String> roles = new java.util.ArrayList<>();
                while (rs.next()) {
                    roles.add(rs.getString("id"));
                }
                assertTrue(roles.containsAll(List.of("ADMIN", "CUSTOMER", "ORGANIZER", "STAFF")),
                    "Roles must contain ADMIN, CUSTOMER, ORGANIZER, STAFF, got: " + roles);
            }

            // 3. Verify user_roles table exists and has backfilled records
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM ticketing.user_roles")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 2, "user_roles must have backfilled records for existing users");
            }

            // 4. Verify external_identities table exists
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM ticketing.external_identities")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1)); // empty initially
            }

            // 5. Verify refresh_tokens table exists
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM ticketing.refresh_tokens")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1)); // empty initially
            }

            // 6. Verify password_reset_tokens table exists
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM ticketing.password_reset_tokens")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1)); // empty initially
            }
        }
    }
}
