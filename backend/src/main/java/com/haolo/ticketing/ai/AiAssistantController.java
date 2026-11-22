package com.haolo.ticketing.ai;

import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.ai.AiModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AiTicketingService aiService;
    private final SellOutForecastService forecastService;
    private final AntiScalpingDetectionService antiScalpingService;

    public AiAssistantController(
        AiTicketingService aiService,
        SellOutForecastService forecastService,
        AntiScalpingDetectionService antiScalpingService
    ) {
        this.aiService = aiService;
        this.forecastService = forecastService;
        this.antiScalpingService = antiScalpingService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return aiService.status();
    }

    @PostMapping("/concierge/chat")
    public ConciergeChatResponse conciergeChat(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody ConciergeChatRequest req
    ) {
        return aiService.chatConcierge(principal, req);
    }

    @PostMapping("/organizer/generate-event")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public OrganizerGenerateResponse generateOrganizerEvent(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody OrganizerGenerateRequest req
    ) {
        return aiService.generateOrganizerEvent(principal, req);
    }

    @PostMapping("/admin/diagnose")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDiagnoseResponse diagnoseOps(@RequestBody(required = false) AdminDiagnoseRequest req) {
        return aiService.diagnoseOps(req);
    }

    @GetMapping("/admin/forecast/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public SellOutForecastResponse getEventForecast(@PathVariable UUID eventId) {
        return forecastService.calculateForecast(eventId);
    }

    @GetMapping("/admin/scalping-alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ScalpingAlertDto> getScalpingAlerts(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID eventId
    ) {
        // Tự động quét và phát hiện các trường hợp mới nhất
        antiScalpingService.scanAndDetect();
        return antiScalpingService.getAlerts(status, eventId);
    }

    @PostMapping("/admin/scalping-alerts/{id}/action")
    @PreAuthorize("hasRole('ADMIN')")
    public ScalpingActionResponse takeScalpingAction(
        @PathVariable UUID id,
        @RequestBody(required = false) ScalpingActionRequest req,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        return antiScalpingService.takeAction(id, req, principal);
    }
}
