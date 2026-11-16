package com.haolo.ticketing.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.admin.AdminModels.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminReportServiceTest {

    private JdbcTemplate jdbc;
    private AdminReportService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new AdminReportService(jdbc);
    }

    @Test
    void testAntiCsvInjectionSanitizer() {
        // 1. Prefixes that trigger spreadsheet formula execution: =, +, -, @, Tab, CR
        assertEquals("'=SUM(A1:A10)", AdminReportService.sanitizeCsvField("=SUM(A1:A10)"));
        assertEquals("'+cmd|' /C calc'!A0", AdminReportService.sanitizeCsvField("+cmd|' /C calc'!A0"));
        assertEquals("'-10+20", AdminReportService.sanitizeCsvField("-10+20"));
        assertEquals("'@dangerous_macro", AdminReportService.sanitizeCsvField("@dangerous_macro"));
        assertEquals("'\tTabDelimited", AdminReportService.sanitizeCsvField("\tTabDelimited"));

        // 2. Normal text remains unescaped
        assertEquals("Đại Nhạc Hội Mùa Thu", AdminReportService.sanitizeCsvField("Đại Nhạc Hội Mùa Thu"));
        assertEquals("123456", AdminReportService.sanitizeCsvField("123456"));
        assertEquals("", AdminReportService.sanitizeCsvField(null));

        // 3. Text with commas or quotes wrapped in quotes with doubled inner quotes
        assertEquals("\"Hà Nội, Việt Nam\"", AdminReportService.sanitizeCsvField("Hà Nội, Việt Nam"));
        assertEquals("\"Nhà hát \"\"Lớn\"\" Hà Nội\"", AdminReportService.sanitizeCsvField("Nhà hát \"Lớn\" Hà Nội"));
    }

    @Test
    void testPlatformSummaryCalculation() {
        when(jdbc.queryForObject(contains("SUM(total_amount_vnd)"), eq(Long.class))).thenReturn(150000000L);
        when(jdbc.queryForObject(contains("count(t.id)"), eq(Long.class))).thenReturn(300L);
        when(jdbc.queryForObject(contains("count(id) FROM ticketing.bookings"), eq(Long.class))).thenReturn(150L);
        when(jdbc.queryForObject(contains("count(id) FROM ticketing.ticket_check_ins"), eq(Long.class))).thenReturn(210L);

        PlatformSummaryDto summary = service.getPlatformSummary();
        assertNotNull(summary);
        assertEquals(150000000L, summary.totalRevenueVnd());
        assertEquals(300L, summary.totalTicketsSold());
        assertEquals(150L, summary.totalConfirmedBookings());
        assertEquals(210L, summary.totalCheckIns());
        assertEquals(70.0, summary.averageOccupancyPercent());
    }

    @Test
    void testGenerateEventsCsvContainsUtf8BomAndHeaders() {
        UUID eventId = UUID.randomUUID();
        when(jdbc.queryForList(contains("SELECT id, title, venue, starts_at, status FROM ticketing.events")))
            .thenReturn(List.of(Map.of(
                "id", eventId,
                "title", "=SUM(1,2) Concert",
                "venue", "Sân Mỹ Đình",
                "starts_at", Timestamp.from(Instant.parse("2026-11-20T19:00:00Z")),
                "status", "PUBLISHED"
            )));

        when(jdbc.queryForObject(contains("SUM(i.capacity)"), eq(Long.class), eq(eventId))).thenReturn(500L);
        when(jdbc.queryForObject(contains("count(id) FROM ticketing.seats"), eq(Long.class), eq(eventId))).thenReturn(500L);
        when(jdbc.queryForObject(contains("seat_id IS NULL"), eq(Long.class), eq(eventId))).thenReturn(400L);
        when(jdbc.queryForObject(contains("seat_id IS NOT NULL"), eq(Long.class), eq(eventId))).thenReturn(350L);
        when(jdbc.queryForObject(contains("SUM(total_amount_vnd)"), eq(Long.class), eq(eventId))).thenReturn(375000000L);

        String csv = service.generateEventsCsv();
        assertNotNull(csv);
        // Verify UTF-8 BOM
        assertTrue(csv.startsWith("\uFEFF"), "CSV must start with UTF-8 BOM");
        assertTrue(csv.contains("Mã Sự Kiện,Tên Sự Kiện,Địa Điểm,Thời Gian"));
        // Verify formula injection escaped
        assertTrue(csv.contains("'=SUM(1,2)"), "Formula in event title must be escaped with single quote");
        assertTrue(csv.contains("375000000"));
        assertTrue(csv.contains("75.0%"));
    }
}
