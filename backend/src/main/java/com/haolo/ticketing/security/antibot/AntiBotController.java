package com.haolo.ticketing.security.antibot;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.security.antibot.AntiBotModels.ChallengeRequest;
import com.haolo.ticketing.security.antibot.AntiBotModels.ChallengeResponse;
import com.haolo.ticketing.security.antibot.AntiBotModels.VerifyChallengeRequest;
import com.haolo.ticketing.security.antibot.AntiBotModels.VerifyChallengeResponse;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/security/anti-bot")
public class AntiBotController {

    private final AntiBotService antiBotService;

    public AntiBotController(AntiBotService antiBotService) {
        this.antiBotService = antiBotService;
    }

    @PostMapping("/challenge")
    public ResponseEntity<ChallengeResponse> getChallenge(
            @RequestBody(required = false) ChallengeRequest req,
            @AuthenticationPrincipal DemoPrincipal principal,
            HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        UUID accountId = principal != null ? principal.accountId() : null;
        UUID eventId = req != null ? req.eventId() : null;
        return ResponseEntity.ok(antiBotService.issueChallenge(clientIp, accountId, eventId));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyChallengeResponse> verify(
            @RequestBody VerifyChallengeRequest req,
            HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        return ResponseEntity.ok(antiBotService.verifyChallenge(req.challengeToken(), req.solution(), clientIp));
    }

    @GetMapping("/events/{eventId}/status")
    public ResponseEntity<Map<String, Object>> getEventAntiBotStatus(@PathVariable UUID eventId) {
        boolean required = antiBotService.isEventAntiBotProtected(eventId);
        return ResponseEntity.ok(Map.of(
            "eventId", eventId,
            "antiBotRequired", required
        ));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
