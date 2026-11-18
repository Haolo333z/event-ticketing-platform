package com.haolo.ticketing.ai;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.ai.AiModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.realtime.BestSeatsService;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiTicketingServiceTest {

    @Test
    void parsesVietnameseQuantityAndBudgetAccurately() {
        assertEquals(3, AiTicketingService.parseRequestedQuantity("tìm giúp mình 3 ghế ngồi cạnh nhau dưới 3 triệu"));
        assertEquals(3_000_000L, AiTicketingService.parseMaxBudgetVnd("tìm giúp mình 3 ghế ngồi cạnh nhau dưới 3 triệu"));
        assertEquals(500_000L, AiTicketingService.parseMaxBudgetVnd("2 vé concert dưới 500k"));
        assertEquals(1_500_000L, AiTicketingService.parseMaxBudgetVnd("dưới 1.5tr"));
    }

    @Test
    void organizerCopilotGeneratesSeoAgendaTiersAndSeatMatrixOffline() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BestSeatsService bestSeats = mock(BestSeatsService.class);
        GeminiAiAdapter gemini = new GeminiAiAdapter("", "gemini-2.0-flash", new ObjectMapper());
        LocalFallbackAiAdapter fallback = new LocalFallbackAiAdapter();
        AiTicketingService service = new AiTicketingService(jdbc, bestSeats, gemini, fallback);

        OrganizerGenerateResponse res = service.generateOrganizerEvent(
            new DemoPrincipal(UUID.randomUUID(), "ORGANIZER", List.of("ORGANIZER")),
            new OrganizerGenerateRequest("Đêm Nhạc Trịnh Công Sơn", "CONCERT", 100, 400_000L)
        );

        assertEquals("LOCAL_FALLBACK", res.adapterUsed());
        assertTrue(res.suggestedTitle().contains("Đêm Nhạc Trịnh Công Sơn"));
        assertEquals(4, res.agenda().size());
        assertEquals(3, res.ticketTiers().size());
        assertNotNull(res.seatMatrixConfig().get("rows"));
        assertEquals(10, res.seatMatrixConfig().get("seatsPerRow"));
    }

    @Test
    void adminDiagnosticsExplainsLateRefundArbitrationClearly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BestSeatsService bestSeats = mock(BestSeatsService.class);
        GeminiAiAdapter gemini = new GeminiAiAdapter("", "gemini-2.0-flash", new ObjectMapper());
        LocalFallbackAiAdapter fallback = new LocalFallbackAiAdapter();
        AiTicketingService service = new AiTicketingService(jdbc, bestSeats, gemini, fallback);

        UUID bookingId = UUID.randomUUID();
        Map<String, Object> bookingRow = new LinkedHashMap<>();
        bookingRow.put("id", bookingId);
        bookingRow.put("status", "REFUND_REQUIRED");
        bookingRow.put("total_amount_vnd", 600_000L);
        bookingRow.put("expires_at", "2026-11-18T10:00:00Z");
        bookingRow.put("expired_at", "2026-11-18T10:00:05Z");

        when(jdbc.queryForList(contains("FROM ticketing.bookings WHERE id"), eq(bookingId)))
            .thenReturn(List.of(bookingRow));
        when(jdbc.queryForList(contains("FROM ticketing.refund_attempts"), eq(bookingId)))
            .thenReturn(List.of());

        AdminDiagnoseResponse diag = service.diagnoseOps(new AdminDiagnoseRequest(bookingId, null));
        assertEquals("HIGH", diag.severity());
        assertFalse(diag.safeToReplay());
        assertTrue(diag.rootCause().contains("Zero Oversell"));
        assertTrue(diag.recommendedAction().contains("APPROVE"));
    }

    @Test
    void conciergeRequiresLoginForOrderLookupToEnforceAntiIdor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BestSeatsService bestSeats = mock(BestSeatsService.class);
        GeminiAiAdapter gemini = new GeminiAiAdapter("", "gemini-2.0-flash", new ObjectMapper());
        LocalFallbackAiAdapter fallback = new LocalFallbackAiAdapter();
        AiTicketingService service = new AiTicketingService(jdbc, bestSeats, gemini, fallback);

        ConciergeChatResponse res = service.chatConcierge(null, new ConciergeChatRequest(null, "Kiểm tra trạng thái đơn hàng và hoàn tiền của tôi"));
        assertEquals("LOCAL_FALLBACK", res.adapterUsed());
        assertTrue(res.reply().contains("Anti-IDOR") || res.reply().contains("đăng nhập"));
        assertTrue(res.bookingSummaries().isEmpty());
    }

    @Test
    void conciergeAnswersGreetingAndHelpNaturally() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BestSeatsService bestSeats = mock(BestSeatsService.class);
        GeminiAiAdapter gemini = new GeminiAiAdapter("", "gemini-2.0-flash", new ObjectMapper());
        LocalFallbackAiAdapter fallback = new LocalFallbackAiAdapter();
        AiTicketingService service = new AiTicketingService(jdbc, bestSeats, gemini, fallback);

        ConciergeChatResponse res = service.chatConcierge(null, new ConciergeChatRequest(null, "bạn có thể giúp gì cho tôi"));
        assertEquals("LOCAL_FALLBACK", res.adapterUsed());
        assertTrue(res.reply().contains("EventFlow AI Concierge"));
        assertTrue(res.suggestions().isEmpty());
    }
}
