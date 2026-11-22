package com.haolo.ticketing.seatview;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.seatview.SeatViewModels.SeatViewDto;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeatViewServiceTest {

    private JdbcTemplate jdbc;
    private SeatViewService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new SeatViewService(jdbc);
    }

    @Test
    void testGetSeatView_Success() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        SeatViewDto mockDto = new SeatViewDto(
            seatId, eventId, "A05", "A", 5, 10.5, 12.0, 96,
            "Tầm nhìn trực diện sân khấu VIP",
            "<svg>test</svg>", "AVAILABLE", 500000L, "VIP Seated"
        );

        when(jdbc.query(anyString(), any(RowMapper.class), eq(eventId), eq(seatId)))
            .thenReturn(List.of(mockDto));

        SeatViewDto res = service.getSeatView(eventId, seatId);
        assertNotNull(res);
        assertEquals(seatId, res.seatId());
        assertEquals("A05", res.seatLabel());
        assertEquals(10.5, res.distanceMeters());
        assertEquals(96, res.viewQualityScore());
        assertTrue(res.perspectiveSvg().contains("test"));
    }

    @Test
    void testGetSeatView_NotFound() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        when(jdbc.query(anyString(), any(RowMapper.class), eq(eventId), eq(seatId)))
            .thenReturn(Collections.emptyList());

        ApiException ex = assertThrows(ApiException.class, () -> service.getSeatView(eventId, seatId));
        assertEquals(404, ex.status);
        assertEquals("SEAT_NOT_FOUND", ex.code);
    }

    @Test
    void testGenerateStagePerspectiveSvg() {
        String svg = service.generateStagePerspectiveSvg("B", 4, 12.5, 12.0, 92);
        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("SÂN KHẤU CHÍNH"));
        assertTrue(svg.contains("Ghế B4"));
        assertTrue(svg.contains("12.5m"));
        assertTrue(svg.contains("92/100"));
    }
}
