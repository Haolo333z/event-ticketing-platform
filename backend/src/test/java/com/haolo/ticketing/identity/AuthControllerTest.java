package com.haolo.ticketing.identity;

import java.util.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.haolo.ticketing.shared.ApiException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private JdbcTemplate jdbc;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = new JwtService("test-secret-key-for-auth-controller-unit-tests-hmac256-sufficiently-long", 900, new ObjectMapper());
        refreshTokenService = mock(RefreshTokenService.class);
        controller = new AuthController(jdbc, passwordEncoder, jwtService, refreshTokenService);
    }

    @Test
    void registerValidUserReturnsCreatedWithCookieAndRoles() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), eq("test@eventflow.vn"))).thenReturn(0);
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$10$hashedPasswordValue");
        when(refreshTokenService.createRefreshToken(any(UUID.class))).thenReturn("raw_refresh_token_64chars");

        var req = new AuthController.RegisterRequest("Nguyen Van A", "test@eventflow.vn", "Secret123", "0901234567");
        ResponseEntity<AuthController.AuthResponse> res = controller.register(req, response);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertNotNull(res.getBody().token());
        assertEquals("Nguyen Van A", res.getBody().user().displayName());
        assertEquals("CUSTOMER", res.getBody().user().role());
        assertTrue(res.getBody().user().roles().contains("CUSTOMER"));

        // Verify Set-Cookie header was set
        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), contains("eventflow_refresh=raw_refresh_token_64chars"));
        verify(jdbc, times(1)).update(contains("INSERT INTO ticketing.user_roles"), any(UUID.class));
    }

    @Test
    void registerWithShortPasswordThrowsValidation() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        var req = new AuthController.RegisterRequest("User", "test@eventflow.vn", "12345", "090");
        ApiException ex = assertThrows(ApiException.class, () -> controller.register(req, response));
        assertEquals(400, ex.status);
        assertEquals("VALIDATION_ERROR", ex.code);
    }

    @Test
    void loginWithCorrectPasswordReturnsTokenAndSetsCookie() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        UUID userId = UUID.randomUUID();

        // Mock user lookup
        when(jdbc.query(contains("SELECT id, demo_role"), any(RowMapper.class), eq("admin@eventflow.vn")))
            .thenReturn(List.of(new AuthController.UserDbRow(
                userId, "ADMIN", "Admin User", "admin@eventflow.vn", "$2a$10$hashedPassword", "0909999999"
            )));
        // Mock roles lookup
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of("ADMIN"));

        when(passwordEncoder.matches("Admin@123", "$2a$10$hashedPassword")).thenReturn(true);
        when(refreshTokenService.createRefreshToken(userId)).thenReturn("admin_refresh_token");

        var req = new AuthController.LoginRequest("admin@eventflow.vn", "Admin@123");
        ResponseEntity<AuthController.AuthResponse> res = controller.login(req, response);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("ADMIN", res.getBody().user().role());
        assertTrue(res.getBody().user().roles().contains("ADMIN"));

        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), contains("eventflow_refresh=admin_refresh_token"));
    }

    @Test
    void loginWithWrongPasswordThrowsUnauthorized() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        UUID userId = UUID.randomUUID();

        when(jdbc.query(contains("SELECT id, demo_role"), any(RowMapper.class), eq("user@eventflow.vn")))
            .thenReturn(List.of(new AuthController.UserDbRow(
                userId, "CUSTOMER", "User", "user@eventflow.vn", "$2a$10$hashedPassword", "090"
            )));

        when(passwordEncoder.matches("WrongPass", "$2a$10$hashedPassword")).thenReturn(false);

        var req = new AuthController.LoginRequest("user@eventflow.vn", "WrongPass");
        ApiException ex = assertThrows(ApiException.class, () -> controller.login(req, response));
        assertEquals(401, ex.status);
        assertEquals("INVALID_CREDENTIALS", ex.code);
    }

    @Test
    void refreshWithValidCookieRotatesTokenAndReturnsNewAccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        UUID userId = UUID.randomUUID();

        Cookie refreshCookie = new Cookie("eventflow_refresh", "incoming_raw_token");
        when(request.getCookies()).thenReturn(new Cookie[] { refreshCookie });

        when(refreshTokenService.rotateRefreshToken("incoming_raw_token"))
            .thenReturn(new RefreshTokenService.RotationResult(userId, "new_rotated_raw_token"));

        when(jdbc.query(contains("SELECT id, demo_role, display_name"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of(new AuthController.UserMeRow(
                userId, "CUSTOMER", "Rotated User", "user@eventflow.vn", "090"
            )));
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of("CUSTOMER"));

        ResponseEntity<AuthController.AuthResponse> res = controller.refresh(request, response);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("Rotated User", res.getBody().user().displayName());

        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), contains("eventflow_refresh=new_rotated_raw_token"));
    }

    @Test
    void logoutClearsCookieAndRevokesToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        Cookie refreshCookie = new Cookie("eventflow_refresh", "token_to_logout");
        when(request.getCookies()).thenReturn(new Cookie[] { refreshCookie });

        ResponseEntity<Map<String, String>> res = controller.logout(request, response);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(refreshTokenService, times(1)).revokeByRawToken("token_to_logout");
        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), contains("Max-Age=0"));
    }

    @Test
    void meReturnsUserProfileWithMultiRoles() {
        UUID userId = UUID.randomUUID();
        DemoPrincipal principal = new DemoPrincipal(userId, "ADMIN", List.of("ADMIN", "CUSTOMER"));

        when(jdbc.query(contains("SELECT id, demo_role, display_name"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of(new AuthController.UserMeRow(
                userId, "ADMIN", "Admin User", "admin@eventflow.vn", "0909999999"
            )));
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of("ADMIN", "CUSTOMER"));

        ResponseEntity<AuthController.UserDto> res = controller.me(principal);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(userId, res.getBody().id());
        assertEquals(2, res.getBody().roles().size());
        assertTrue(res.getBody().roles().contains("ADMIN"));
        assertTrue(res.getBody().roles().contains("CUSTOMER"));
    }
}
