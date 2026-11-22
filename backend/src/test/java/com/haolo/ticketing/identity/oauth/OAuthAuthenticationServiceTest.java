package com.haolo.ticketing.identity.oauth;

import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.identity.AuthController.AuthResponse;
import com.haolo.ticketing.identity.JwtService;
import com.haolo.ticketing.identity.RefreshTokenService;
import com.haolo.ticketing.identity.oauth.OAuthModels.*;
import com.haolo.ticketing.shared.ApiException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OAuthAuthenticationServiceTest {

    private JdbcTemplate jdbc;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private GoogleOAuthAdapter googleOAuthAdapter;
    private LocalDemoGoogleAdapter localDemoGoogleAdapter;
    private OAuthAuthenticationService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        googleOAuthAdapter = new GoogleOAuthAdapter("", "", new ObjectMapper());
        localDemoGoogleAdapter = new LocalDemoGoogleAdapter();

        service = new OAuthAuthenticationService(
            jdbc,
            jwtService,
            refreshTokenService,
            googleOAuthAdapter,
            localDemoGoogleAdapter
        );
    }

    @Test
    void testGetGoogleConfig_DemoMode() {
        OAuthProviderConfigResponse config = service.getGoogleConfig();
        assertNotNull(config);
        assertEquals("GOOGLE", config.provider());
        assertEquals("DEMO", config.mode());
        assertEquals(3, config.demoAccounts().size());
        assertEquals("customer.google@eventflow.vn", config.demoAccounts().get(0).email());
    }

    @Test
    void testProcessGoogleCallback_NewUser_Success() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        OAuthCallbackRequest req = new OAuthCallbackRequest(null, null, "google-demo-customer");

        // mock oauth_identities check (empty -> not linked)
        when(jdbc.query(contains("FROM ticketing.oauth_identities"), any(RowMapper.class), any()))
            .thenReturn(Collections.emptyList());

        // mock users check by email (empty -> new user)
        when(jdbc.query(contains("FROM ticketing.users WHERE LOWER(email)"), any(RowMapper.class), any()))
            .thenReturn(Collections.emptyList());

        // mock user roles check
        when(jdbc.query(contains("FROM ticketing.user_roles"), any(RowMapper.class), any()))
            .thenReturn(List.of("CUSTOMER"));

        when(jwtService.generateToken(any(), any(), any(), any(), any())).thenReturn("mock-jwt-token");
        when(refreshTokenService.createRefreshToken(any())).thenReturn("mock-refresh-token");

        AuthResponse auth = service.processGoogleCallback(req, response);
        assertNotNull(auth);
        assertEquals("mock-jwt-token", auth.token());
        assertEquals("customer.google@eventflow.vn", auth.user().email());
        assertEquals("CUSTOMER", auth.user().role());

        // Verify insertions
        verify(jdbc, atLeastOnce()).update(contains("INSERT INTO ticketing.users"), any(), any(), any(), any());
        verify(jdbc, atLeastOnce()).update(contains("INSERT INTO ticketing.oauth_identities"), any(), any(), any(), any(), any());
    }

    @Test
    void testProcessGoogleCallback_ExistingLinkedUser_Success() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        OAuthCallbackRequest req = new OAuthCallbackRequest(null, null, "google-demo-vip");
        UUID existingUserId = UUID.randomUUID();

        // mock oauth_identities query returns existing user id
        when(jdbc.query(contains("FROM ticketing.oauth_identities"), any(RowMapper.class), anyString()))
            .thenReturn(List.of(existingUserId));

        // mock user details
        when(jdbc.query(contains("FROM ticketing.users WHERE id = ?"), any(RowMapper.class), any(UUID.class)))
            .thenReturn(List.of(new OAuthAuthenticationService.UserInfoRow(
                existingUserId, "CUSTOMER", "Trần Thị VIP Google", "vip.google@eventflow.vn", "0901234567", "ACTIVE"
            )));

        when(jdbc.query(contains("FROM ticketing.user_roles"), any(RowMapper.class), any(UUID.class)))
            .thenReturn(List.of("CUSTOMER"));

        when(jwtService.generateToken(any(), any(), any(), any(), any())).thenReturn("mock-jwt-vip");
        when(refreshTokenService.createRefreshToken(any())).thenReturn("mock-refresh-vip");

        AuthResponse auth = service.processGoogleCallback(req, response);
        assertNotNull(auth);
        assertEquals("mock-jwt-vip", auth.token());
        assertEquals("vip.google@eventflow.vn", auth.user().email());
        assertEquals("Trần Thị VIP Google", auth.user().displayName());

        verify(jdbc).update(contains("UPDATE ticketing.oauth_identities SET display_name"), any(), any(), any());
    }

    @Test
    void testProcessGoogleCallback_AccountLocked_ThrowsForbidden() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        OAuthCallbackRequest req = new OAuthCallbackRequest(null, null, "google-demo-customer");
        UUID lockedUserId = UUID.randomUUID();

        when(jdbc.query(contains("FROM ticketing.oauth_identities"), any(RowMapper.class), anyString()))
            .thenReturn(List.of(lockedUserId));

        when(jdbc.query(contains("FROM ticketing.users WHERE id = ?"), any(RowMapper.class), any(UUID.class)))
            .thenReturn(List.of(new OAuthAuthenticationService.UserInfoRow(
                lockedUserId, "CUSTOMER", "Nguyễn Văn Khóa", "customer.google@eventflow.vn", "", "LOCKED"
            )));

        ApiException ex = assertThrows(ApiException.class, () -> service.processGoogleCallback(req, response));
        assertEquals(403, ex.status);
        assertEquals("ACCOUNT_LOCKED", ex.code);
    }

    @Test
    void testProcessGoogleCallback_NullRequest_ThrowsBadRequest() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ApiException ex = assertThrows(ApiException.class, () -> service.processGoogleCallback(null, response));
        assertEquals(400, ex.status);
    }
}
