package com.haolo.ticketing.identity;

import java.util.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.haolo.ticketing.shared.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileControllerTest {
    private JdbcTemplate jdbc;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private UserProfileController controller;

    private final UUID testUserId = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final DemoPrincipal principal = new DemoPrincipal(testUserId, "CUSTOMER", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        controller = new UserProfileController(jdbc, passwordEncoder, refreshTokenService);
    }

    @Test
    void getProfileReturnsUserData() {
        when(jdbc.query(contains("SELECT id, email, display_name"), any(RowMapper.class), eq(testUserId)))
            .thenReturn(List.of(new UserProfileController.UserProfileDbRow(
                testUserId, "customer@eventflow.vn", "Nguyễn Văn A", "0901234567", "https://avatar.png", "CUSTOMER"
            )));
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(testUserId)))
            .thenReturn(List.of("CUSTOMER"));
        when(jdbc.queryForObject(contains("SELECT count(*) FROM ticketing.oauth_identities"), eq(Integer.class), eq(testUserId)))
            .thenReturn(1);

        ResponseEntity<UserProfileController.ProfileResponse> res = controller.getProfile(principal);

        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
        var body = res.getBody();
        assertNotNull(body);
        assertEquals("customer@eventflow.vn", body.email());
        assertEquals("Nguyễn Văn A", body.displayName());
        assertTrue(body.googleLinked());
    }

    @Test
    void unauthenticatedThrows401() {
        assertThrows(ApiException.class, () -> controller.getProfile(null));
        assertThrows(ApiException.class, () -> controller.updateProfile(null, new UserProfileController.UpdateProfileRequest("Test", "090", null)));
        assertThrows(ApiException.class, () -> controller.changePassword(null, new UserProfileController.ChangePasswordRequest("old", "new123")));
    }

    @Test
    void updateProfileValidatesDisplayName() {
        var badReq = new UserProfileController.UpdateProfileRequest("", "090", null);
        ApiException ex = assertThrows(ApiException.class, () -> controller.updateProfile(principal, badReq));
        assertEquals(400, ex.status);
    }

    @Test
    void updateProfileSucceeds() {
        when(jdbc.update(contains("UPDATE ticketing.users SET display_name"), eq("Tên Mới"), eq("0988888888"), eq("https://new.png"), eq(testUserId)))
            .thenReturn(1);

        when(jdbc.query(contains("SELECT id, email, display_name"), any(RowMapper.class), eq(testUserId)))
            .thenReturn(List.of(new UserProfileController.UserProfileDbRow(
                testUserId, "customer@eventflow.vn", "Tên Mới", "0988888888", "https://new.png", "CUSTOMER"
            )));

        var req = new UserProfileController.UpdateProfileRequest("Tên Mới", "0988888888", "https://new.png");
        var res = controller.updateProfile(principal, req);
        assertNotNull(res.getBody());
        assertEquals("Tên Mới", res.getBody().displayName());
    }

    @Test
    void changePasswordValidatesLength() {
        var req = new UserProfileController.ChangePasswordRequest("old123", "123");
        ApiException ex = assertThrows(ApiException.class, () -> controller.changePassword(principal, req));
        assertEquals(400, ex.status);
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        when(jdbc.query(contains("SELECT password_hash"), any(RowMapper.class), eq(testUserId)))
            .thenReturn(List.of("$2a$10$hashedoldpassword"));
        when(passwordEncoder.matches("wrongold", "$2a$10$hashedoldpassword")).thenReturn(false);

        var req = new UserProfileController.ChangePasswordRequest("wrongold", "newsecret123");
        ApiException ex = assertThrows(ApiException.class, () -> controller.changePassword(principal, req));
        assertEquals(400, ex.status);
        assertEquals("INVALID_OLD_PASSWORD", ex.code);
    }

    @Test
    void changePasswordSucceedsWhenOldPasswordMatches() {
        when(jdbc.query(contains("SELECT password_hash"), any(RowMapper.class), eq(testUserId)))
            .thenReturn(List.of("$2a$10$hashedoldpassword"));
        when(passwordEncoder.matches("correctold", "$2a$10$hashedoldpassword")).thenReturn(true);
        when(passwordEncoder.encode("newsecret123")).thenReturn("$2a$10$hashednewpassword");

        var req = new UserProfileController.ChangePasswordRequest("correctold", "newsecret123");
        var res = controller.changePassword(principal, req);
        assertEquals(200, res.getStatusCode().value());
        verify(jdbc, times(1)).update(contains("UPDATE ticketing.users SET password_hash = ?"), eq("$2a$10$hashednewpassword"), eq(testUserId));
    }

    @Test
    void getAndPutVatDefaults() {
        when(jdbc.query(contains("SELECT vat_company_name"), any(RowMapper.class), eq(testUserId)))
            .thenReturn(List.of(new UserProfileController.VatDefaultsDto("Công ty ABC", "0101234567", "Hà Nội", "vat@abc.vn")));

        var res = controller.getVatDefaults(principal);
        assertEquals("Công ty ABC", res.getBody().companyName());
        assertEquals("0101234567", res.getBody().taxCode());

        var updateReq = new UserProfileController.VatDefaultsDto("Công ty XYZ", "0309999999", "HCM", "vat@xyz.vn");
        when(jdbc.query(contains("SELECT vat_company_name"), any(RowMapper.class), eq(testUserId)))
            .thenReturn(List.of(updateReq));

        var updateRes = controller.updateVatDefaults(principal, updateReq);
        assertEquals("Công ty XYZ", updateRes.getBody().companyName());
        verify(jdbc, times(1)).update(contains("UPDATE ticketing.users SET vat_company_name = ?"), eq("Công ty XYZ"), eq("0309999999"), eq("HCM"), eq("vat@xyz.vn"), eq(testUserId));
    }

    @Test
    void getSessionsAndRevoke() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getCookies()).thenReturn(new Cookie[] { new Cookie(AuthController.REFRESH_COOKIE_NAME, "rawcookie123") });

        UUID currentFam = UUID.randomUUID();
        when(refreshTokenService.findFamilyIdByRawToken("rawcookie123")).thenReturn(currentFam);
        when(refreshTokenService.getActiveSessions(testUserId, currentFam)).thenReturn(List.of());

        var sessionsRes = controller.getSessions(principal, req);
        assertEquals(200, sessionsRes.getStatusCode().value());

        UUID famToRevoke = UUID.randomUUID();
        var revokeRes = controller.revokeSession(principal, famToRevoke);
        assertEquals(200, revokeRes.getStatusCode().value());
        verify(refreshTokenService, times(1)).revokeFamily(testUserId, famToRevoke);

        var revokeOthersRes = controller.revokeOthers(principal, req);
        assertEquals(200, revokeOthersRes.getStatusCode().value());
        verify(refreshTokenService, times(1)).revokeOtherFamilies(testUserId, currentFam);
    }
}
