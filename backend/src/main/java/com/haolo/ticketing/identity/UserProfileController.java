package com.haolo.ticketing.identity;

import java.util.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/me")
public class UserProfileController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserProfileController(
        JdbcTemplate jdbc,
        PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public record ProfileResponse(
        UUID id,
        String email,
        String displayName,
        String phone,
        String avatarUrl,
        String demoRole,
        List<String> roles,
        boolean googleLinked
    ) {}

    public record UpdateProfileRequest(
        String displayName,
        String phone,
        String avatarUrl
    ) {}

    public record ChangePasswordRequest(
        String oldPassword,
        String newPassword
    ) {}

    public record VatDefaultsDto(
        String companyName,
        String taxCode,
        String companyAddress,
        String recipientEmail
    ) {}

    record UserProfileDbRow(
        UUID id,
        String email,
        String displayName,
        String phone,
        String avatarUrl,
        String demoRole
    ) {}

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal DemoPrincipal principal) {
        assertAuthenticated(principal);
        UUID userId = principal.accountId();

        var rows = jdbc.query(
            "SELECT id, email, display_name, phone, avatar_url, demo_role FROM ticketing.users WHERE id = ?",
            (rs, i) -> new UserProfileDbRow(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("phone"),
                rs.getString("avatar_url"),
                rs.getString("demo_role")
            ),
            userId
        );

        if (rows.isEmpty()) {
            throw new ApiException(404, "USER_NOT_FOUND", "Không tìm thấy thông tin tài khoản");
        }

        UserProfileDbRow r = rows.getFirst();
        String demoRole = r.demoRole();
        List<String> roles = fetchUserRoles(userId, demoRole);

        Integer googleCount = null;
        try {
            googleCount = jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.oauth_identities WHERE user_id = ? AND provider = 'GOOGLE'",
                Integer.class,
                userId
            );
        } catch (Exception ignored) {}
        boolean googleLinked = googleCount != null && googleCount > 0;

        return ResponseEntity.ok(new ProfileResponse(
            r.id(),
            r.email(),
            r.displayName(),
            r.phone(),
            r.avatarUrl(),
            demoRole,
            roles,
            googleLinked
        ));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody UpdateProfileRequest req
    ) {
        assertAuthenticated(principal);
        if (req == null || req.displayName() == null || req.displayName().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Họ tên không được để trống");
        }

        jdbc.update(
            "UPDATE ticketing.users SET display_name = ?, phone = ?, avatar_url = ? WHERE id = ?",
            req.displayName().trim(),
            req.phone() != null ? req.phone().trim() : "",
            req.avatarUrl() != null ? req.avatarUrl().trim() : null,
            principal.accountId()
        );

        return getProfile(principal);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody ChangePasswordRequest req
    ) {
        assertAuthenticated(principal);
        if (req == null || req.newPassword() == null || req.newPassword().length() < 6) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mật khẩu mới phải có ít nhất 6 ký tự");
        }

        UUID userId = principal.accountId();
        var hashes = jdbc.query(
            "SELECT password_hash FROM ticketing.users WHERE id = ?",
            (rs, i) -> rs.getString("password_hash"),
            userId
        );

        if (hashes.isEmpty()) {
            throw new ApiException(404, "USER_NOT_FOUND", "Không tìm thấy người dùng");
        }

        String currentHash = hashes.getFirst();
        if (currentHash != null && !currentHash.isBlank()) {
            if (req.oldPassword() == null || !passwordEncoder.matches(req.oldPassword(), currentHash)) {
                throw new ApiException(400, "INVALID_OLD_PASSWORD", "Mật khẩu cũ không chính xác");
            }
        }

        String newHash = passwordEncoder.encode(req.newPassword());
        jdbc.update("UPDATE ticketing.users SET password_hash = ? WHERE id = ?", newHash, userId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Đổi mật khẩu thành công"));
    }

    @GetMapping("/vat-defaults")
    public ResponseEntity<VatDefaultsDto> getVatDefaults(@AuthenticationPrincipal DemoPrincipal principal) {
        assertAuthenticated(principal);
        var rows = jdbc.query(
            "SELECT vat_company_name, vat_tax_code, vat_company_address, vat_recipient_email FROM ticketing.users WHERE id = ?",
            (rs, i) -> new VatDefaultsDto(
                rs.getString("vat_company_name"),
                rs.getString("vat_tax_code"),
                rs.getString("vat_company_address"),
                rs.getString("vat_recipient_email")
            ),
            principal.accountId()
        );

        VatDefaultsDto dto = rows.isEmpty() ? new VatDefaultsDto(null, null, null, null) : rows.getFirst();
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/vat-defaults")
    public ResponseEntity<VatDefaultsDto> updateVatDefaults(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody VatDefaultsDto req
    ) {
        assertAuthenticated(principal);
        jdbc.update(
            "UPDATE ticketing.users SET vat_company_name = ?, vat_tax_code = ?, vat_company_address = ?, vat_recipient_email = ? WHERE id = ?",
            req != null ? req.companyName() : null,
            req != null ? req.taxCode() : null,
            req != null ? req.companyAddress() : null,
            req != null ? req.recipientEmail() : null,
            principal.accountId()
        );
        return getVatDefaults(principal);
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(
        @AuthenticationPrincipal DemoPrincipal principal,
        HttpServletRequest request
    ) {
        assertAuthenticated(principal);
        String rawToken = extractCookie(request, AuthController.REFRESH_COOKIE_NAME);
        UUID currentFamilyId = refreshTokenService.findFamilyIdByRawToken(rawToken);

        var sessions = refreshTokenService.getActiveSessions(principal.accountId(), currentFamilyId);
        return ResponseEntity.ok(Map.of("sessions", sessions, "total", sessions.size()));
    }

    @DeleteMapping("/sessions/{familyId}")
    public ResponseEntity<Map<String, Object>> revokeSession(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable UUID familyId
    ) {
        assertAuthenticated(principal);
        refreshTokenService.revokeFamily(principal.accountId(), familyId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã thu hồi phiên hoạt động"));
    }

    @DeleteMapping("/sessions/revoke-others")
    public ResponseEntity<Map<String, Object>> revokeOthers(
        @AuthenticationPrincipal DemoPrincipal principal,
        HttpServletRequest request
    ) {
        assertAuthenticated(principal);
        String rawToken = extractCookie(request, AuthController.REFRESH_COOKIE_NAME);
        UUID currentFamilyId = refreshTokenService.findFamilyIdByRawToken(rawToken);

        refreshTokenService.revokeOtherFamilies(principal.accountId(), currentFamilyId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã đăng xuất khỏi tất cả thiết bị khác"));
    }

    private void assertAuthenticated(DemoPrincipal principal) {
        if (principal == null || principal.accountId() == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        }
    }

    private List<String> fetchUserRoles(UUID userId, String fallbackRole) {
        List<String> roles = Collections.emptyList();
        try {
            roles = jdbc.query(
                "SELECT role_id FROM ticketing.user_roles WHERE user_id = ? ORDER BY role_id",
                (rs, rowNum) -> rs.getString("role_id"),
                userId
            );
        } catch (Exception ignored) {
        }
        if (roles.isEmpty() && fallbackRole != null) {
            return List.of(fallbackRole);
        }
        return roles.isEmpty() ? List.of("CUSTOMER") : roles;
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
