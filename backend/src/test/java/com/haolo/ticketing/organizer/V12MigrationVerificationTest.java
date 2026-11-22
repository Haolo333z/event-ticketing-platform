package com.haolo.ticketing.organizer;

import java.sql.*;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V12MigrationVerificationTest {
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
    void applyAndVerifyV12Migration() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDbAvailable(), "PostgreSQL is not reachable on localhost:5432");
        System.setProperty("user.timezone", "UTC");
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V12 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Verify V12 recorded in flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '12'")) {
                assertTrue(rs.next(), "V12 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V12 must be marked successful");
            }

            // 2. Verify venues table seeded with at least 4 venues
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM ticketing.venues")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 4, "Must have at least 4 venues seeded");
            }

            // 3. Verify event_categories table seeded with 4 categories
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM ticketing.event_categories ORDER BY id")) {
                List<String> categories = new java.util.ArrayList<>();
                while (rs.next()) {
                    categories.add(rs.getString("id"));
                }
                assertTrue(categories.containsAll(List.of("CONCERT", "CONFERENCE", "SPORTS", "THEATER")),
                    "Categories must contain CONCERT, CONFERENCE, SPORTS, THEATER");
            }

            // 4. Verify columns organizer_id, category_id, venue_id in events table
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = 'ticketing' AND table_name = 'events'")) {
                List<String> cols = new java.util.ArrayList<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertTrue(cols.contains("organizer_id"), "events must have organizer_id column");
                assertTrue(cols.contains("category_id"), "events must have category_id column");
                assertTrue(cols.contains("venue_id"), "events must have venue_id column");
            }

            // 5. Verify new tables exist: organizer_profiles, event_media, event_staff_assignments
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'ticketing'")) {
                List<String> tables = new java.util.ArrayList<>();
                while (rs.next()) {
                    tables.add(rs.getString("table_name"));
                }
                assertTrue(tables.contains("organizer_profiles"), "organizer_profiles table must exist");
                assertTrue(tables.contains("venues"), "venues table must exist");
                assertTrue(tables.contains("event_categories"), "event_categories table must exist");
                assertTrue(tables.contains("event_media"), "event_media table must exist");
                assertTrue(tables.contains("event_staff_assignments"), "event_staff_assignments table must exist");
            }
        }
    }
}
