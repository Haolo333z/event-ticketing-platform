package com.haolo.ticketing.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.haolo.ticketing.ai.AiModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;

class AiOpsV4ControllerTest {

    private AiTicketingService aiService;
    private SellOutForecastService forecastService;
    private AntiScalpingDetectionService antiScalpingService;
    private AiAssistantController controller;

    private final DemoPrincipal adminPrincipal = new DemoPrincipal(UUID.randomUUID(), "admin@eventflow.vn", List.of("ROLE_ADMIN"));

    @BeforeEach
    void setUp() {
        aiService = mock(AiTicketingService.class);
        forecastService = mock(SellOutForecastService.class);
        antiScalpingService = mock(AntiScalpingDetectionService.class);
        controller = new AiAssistantController(aiService, forecastService, antiScalpingService);
    }

    @Test
    void testGetEventForecast() {
        UUID eventId = UUID.randomUUID();
        SellOutForecastResponse mockRes = new SellOutForecastResponse(
            eventId, "Tech Conference", "PUBLISHED", 500, 400, 50, 50, 240, 10.0,
            10.0, 90.0, 20, 5.0, "HIGH_DEMAND", true, "Nhu cầu cao", "local-fallback-v4"
        );
        when(forecastService.calculateForecast(eventId)).thenReturn(mockRes);

        SellOutForecastResponse res = controller.getEventForecast(eventId);
        assertNotNull(res);
        assertEquals(eventId, res.eventId());
        assertEquals("HIGH_DEMAND", res.sellOutStatus());
        verify(forecastService).calculateForecast(eventId);
    }

    @Test
    void testGetScalpingAlerts() {
        UUID alertId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ScalpingAlertDto dto = new ScalpingAlertDto(
            alertId, "MASS_HOLD_SAME_IP", eventId, "Concert Event",
            UUID.randomUUID(), "bot@test.com", "Bot User", "10.0.0.1", "HIGH",
            "Nghi vấn bot gom vé", "NEW", Map.of("count", 5), Instant.now(), null
        );
        when(antiScalpingService.getAlerts(any(), any())).thenReturn(List.of(dto));

        List<ScalpingAlertDto> list = controller.getScalpingAlerts("NEW", eventId);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("MASS_HOLD_SAME_IP", list.get(0).alertType());
        verify(antiScalpingService).scanAndDetect();
        verify(antiScalpingService).getAlerts("NEW", eventId);
    }

    @Test
    void testTakeScalpingAction() {
        UUID alertId = UUID.randomUUID();
        ScalpingActionRequest req = new ScalpingActionRequest("LOCK_ACCOUNT", "Khóa ngay");
        ScalpingActionResponse mockRes = new ScalpingActionResponse(alertId, "LOCK_ACCOUNT", "ACTION_TAKEN", "Đã khóa", 2);
        when(antiScalpingService.takeAction(eq(alertId), any(), eq(adminPrincipal))).thenReturn(mockRes);

        ScalpingActionResponse res = controller.takeScalpingAction(alertId, req, adminPrincipal);
        assertNotNull(res);
        assertEquals("ACTION_TAKEN", res.status());
        assertEquals(2, res.releasedBookingsCount());
        verify(antiScalpingService).takeAction(alertId, req, adminPrincipal);
    }
}
