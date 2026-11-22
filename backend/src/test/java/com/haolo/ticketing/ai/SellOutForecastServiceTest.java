package com.haolo.ticketing.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.ai.AiModels.SellOutForecastResponse;
import com.haolo.ticketing.shared.ApiException;

@ExtendWith(MockitoExtension.class)
class SellOutForecastServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private GeminiAiAdapter geminiAdapter;

    @Mock
    private LocalFallbackAiAdapter fallbackAdapter;

    private SellOutForecastService service;

    @BeforeEach
    void setUp() {
        lenient().when(fallbackAdapter.adapterName()).thenReturn("local-fallback-v4");
        lenient().when(geminiAdapter.isAvailable()).thenReturn(false);
        service = new SellOutForecastService(jdbc, geminiAdapter, fallbackAdapter);
    }

    @Test
    void testCalculateForecast_SellOutImminent() {
        UUID eventId = UUID.randomUUID();

        when(jdbc.queryForMap(contains("FROM ticketing.events"), eq(eventId)))
            .thenReturn(Map.of("title", "Global Tech Summit 2026", "status", "PUBLISHED"));

        // GA capacity = 1000, Seated = 0 -> total 1000
        lenient().when(jdbc.queryForObject(contains("ig.capacity"), eq(Integer.class), eq(eventId))).thenReturn(1000);
        lenient().when(jdbc.queryForObject(contains("ticketing.seats s"), eq(Integer.class), eq(eventId))).thenReturn(0);

        // Sold tickets = 800
        lenient().when(jdbc.queryForObject(contains("tickets WHERE event_id = ? AND status"), eq(Integer.class), eq(eventId))).thenReturn(800);

        // Held tickets = 150
        lenient().when(jdbc.queryForObject(contains("ig.held"), eq(Integer.class), eq(eventId))).thenReturn(150);
        lenient().when(jdbc.queryForObject(contains("status = 'HELD'"), eq(Integer.class), eq(eventId))).thenReturn(0);

        // Sold 24h = 480 -> velocity = 20 tickets/hour
        lenient().when(jdbc.queryForObject(contains("INTERVAL '24 hours'"), eq(Integer.class), eq(eventId))).thenReturn(480);

        // Waitlist = 50
        lenient().when(jdbc.queryForObject(contains("event_waitlists"), eq(Integer.class), eq(eventId))).thenReturn(50);

        SellOutForecastResponse res = service.calculateForecast(eventId);

        assertNotNull(res);
        assertEquals("Global Tech Summit 2026", res.eventTitle());
        assertEquals(1000, res.totalCapacity());
        assertEquals(800, res.soldTickets());
        assertEquals(150, res.heldTickets());
        assertEquals(50, res.availableTickets()); // 1000 - 800 - 150 = 50
        assertEquals(480, res.soldLast24Hours());
        assertEquals(20.0, res.salesVelocityPerHour());
        assertEquals(2.5, res.estimatedHoursToSellOut()); // 50 / 20 = 2.5h, but waitlist=50 >= available=50 -> SELL_OUT_IMMINENT
        assertEquals("SELL_OUT_IMMINENT", res.sellOutStatus());
        assertTrue(res.recommendVirtualWaitingRoom());
        assertTrue(res.recommendationVi().contains("CẢNH BÁO"));
    }

    @Test
    void testCalculateForecast_SoldOut() {
        UUID eventId = UUID.randomUUID();

        when(jdbc.queryForMap(contains("FROM ticketing.events"), eq(eventId)))
            .thenReturn(Map.of("title", "Summer Music Gala 2026", "status", "PUBLISHED"));

        lenient().when(jdbc.queryForObject(contains("ig.capacity"), eq(Integer.class), eq(eventId))).thenReturn(500);
        lenient().when(jdbc.queryForObject(contains("ticketing.seats s"), eq(Integer.class), eq(eventId))).thenReturn(0);

        // Sold tickets = 500
        lenient().when(jdbc.queryForObject(contains("tickets WHERE event_id = ? AND status"), eq(Integer.class), eq(eventId))).thenReturn(500);
        lenient().when(jdbc.queryForObject(contains("ig.held"), eq(Integer.class), eq(eventId))).thenReturn(0);
        lenient().when(jdbc.queryForObject(contains("status = 'HELD'"), eq(Integer.class), eq(eventId))).thenReturn(0);
        lenient().when(jdbc.queryForObject(contains("INTERVAL '24 hours'"), eq(Integer.class), eq(eventId))).thenReturn(300);
        lenient().when(jdbc.queryForObject(contains("event_waitlists"), eq(Integer.class), eq(eventId))).thenReturn(120);

        SellOutForecastResponse res = service.calculateForecast(eventId);

        assertNotNull(res);
        assertEquals(0, res.availableTickets());
        assertEquals(0.0, res.estimatedHoursToSellOut());
        assertEquals("SOLD_OUT", res.sellOutStatus());
        assertTrue(res.recommendationVi().contains("đã bán hết 100%"));
    }

    @Test
    void testCalculateForecast_EventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(jdbc.queryForMap(contains("FROM ticketing.events"), eq(eventId)))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        ApiException ex = assertThrows(ApiException.class, () -> service.calculateForecast(eventId));
        assertEquals("EVENT_NOT_FOUND", ex.code);
    }
}
