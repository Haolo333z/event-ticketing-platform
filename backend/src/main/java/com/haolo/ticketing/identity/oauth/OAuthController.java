package com.haolo.ticketing.identity.oauth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.AuthController.AuthResponse;
import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthCallbackRequest;
import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthProviderConfigResponse;

@RestController
@RequestMapping("/api/auth/oauth")
public class OAuthController {

    private final OAuthAuthenticationService authService;

    public OAuthController(OAuthAuthenticationService authService) {
        this.authService = authService;
    }

    @GetMapping("/google/config")
    public ResponseEntity<OAuthProviderConfigResponse> getGoogleConfig() {
        return ResponseEntity.ok(authService.getGoogleConfig());
    }

    @PostMapping("/google/callback")
    public ResponseEntity<AuthResponse> googleCallback(
        @RequestBody OAuthCallbackRequest request,
        HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.processGoogleCallback(request, response);
        return ResponseEntity.ok(authResponse);
    }
}
