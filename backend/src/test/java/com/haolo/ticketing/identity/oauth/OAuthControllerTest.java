package com.haolo.ticketing.identity.oauth;

import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import com.haolo.ticketing.identity.AuthController.AuthResponse;
import com.haolo.ticketing.identity.AuthController.UserDto;
import com.haolo.ticketing.identity.oauth.OAuthModels.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthControllerTest {

    private OAuthAuthenticationService authService;
    private OAuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(OAuthAuthenticationService.class);
        controller = new OAuthController(authService);
    }

    @Test
    void testGetGoogleConfig() {
        OAuthProviderConfigResponse expectedConfig = new OAuthProviderConfigResponse(
            "GOOGLE", "DEMO", "", List.of()
        );
        when(authService.getGoogleConfig()).thenReturn(expectedConfig);

        ResponseEntity<OAuthProviderConfigResponse> res = controller.getGoogleConfig();
        assertEquals(200, res.getStatusCode().value());
        assertEquals("GOOGLE", res.getBody().provider());
        assertEquals("DEMO", res.getBody().mode());
    }

    @Test
    void testGoogleCallback() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        OAuthCallbackRequest req = new OAuthCallbackRequest(null, null, "google-demo-customer");
        AuthResponse expectedAuth = new AuthResponse(
            "test-token",
            new UserDto(UUID.randomUUID(), "Google User", "google@test.com", "", "CUSTOMER", List.of("CUSTOMER"))
        );
        when(authService.processGoogleCallback(req, response)).thenReturn(expectedAuth);

        ResponseEntity<AuthResponse> res = controller.googleCallback(req, response);
        assertEquals(200, res.getStatusCode().value());
        assertEquals("test-token", res.getBody().token());
        assertEquals("google@test.com", res.getBody().user().email());
    }
}
