package com.haolo.ticketing.ai;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.ai.AiModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AiTicketingService aiService;

    public AiAssistantController(AiTicketingService aiService) {
        this.aiService = aiService;
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
}
