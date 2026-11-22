package com.haolo.ticketing.events;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V29MigrationVerificationTest {
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
    void applyAndVerifyV29Migration() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDbAvailable(), "PostgreSQL is not reachable on localhost:5432");
        System.setProperty("user.timezone", "UTC");
        Flyway flyway = Flyway.configure()
            .dataSource(URL, USER, PASS)
            .defaultSchema("public")
            .locations("classpath:db/migration")
            .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Flyway migration V29 should succeed");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Verify V29 recorded in flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM public.flyway_schema_history WHERE version = '29'")) {
                assertTrue(rs.next(), "V29 must exist in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "V29 must be marked successful");
            }

            // 2. Verify categories have clean UTF-8
            try (ResultSet rs = stmt.executeQuery("SELECT name, description FROM ticketing.event_categories WHERE id = 'CONCERT'")) {
                assertTrue(rs.next());
                assertEquals("Đại nhạc hội & Concert", rs.getString("name"));
                assertEquals("Quy mô 5.000 - 50.000 khán giả mở bán đồng loạt", rs.getString("description"));
            }

            // 3. Verify venues have clean UTF-8
            try (ResultSet rs = stmt.executeQuery("SELECT name, address, city FROM ticketing.venues WHERE id = '50000000-0000-4000-8000-000000000001'")) {
                assertTrue(rs.next());
                assertEquals("Nhà hát Lớn Hà Nội", rs.getString("name"));
                assertEquals("Số 1 Tràng Tiền, Hoàn Kiếm", rs.getString("address"));
                assertEquals("Hà Nội", rs.getString("city"));
            }

            // 4. Verify SEATED events are published and have valid sale_end_at
            try (ResultSet rs = stmt.executeQuery(
                "SELECT count(*) FROM ticketing.events e " +
                "JOIN ticketing.ticket_types tt ON tt.event_id = e.id " +
                "WHERE tt.mode = 'SEATED' AND e.status = 'PUBLISHED' AND e.sale_end_at > clock_timestamp()")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 3, "At least 3 SEATED events must be on sale");
            }
        }
    }
}
