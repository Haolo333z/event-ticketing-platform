package com.haolo.ticketing.identity.oauth;

import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.AuthController.AuthResponse;
import com.haolo.ticketing.identity.AuthController.UserDto;
import com.haolo.ticketing.identity.JwtService;
import com.haolo.ticketing.identity.RefreshTokenService;
import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthCallbackRequest;
import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthProviderConfigResponse;
import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthUserInfo;
import com.haolo.ticketing.shared.ApiException;

@Service
public class OAuthAuthenticationService {

    public static final String REFRESH_COOKIE_NAME = "eventflow_refresh";

    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleOAuthAdapter googleOAuthAdapter;
    private final LocalDemoGoogleAdapter localDemoGoogleAdapter;

    public OAuthAuthenticationService(
        JdbcTemplate jdbc,
        JwtService jwtService,
        RefreshTokenService refreshTokenService,
        GoogleOAuthAdapter googleOAuthAdapter,
        LocalDemoGoogleAdapter localDemoGoogleAdapter
    ) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.googleOAuthAdapter = googleOAuthAdapter;
        this.localDemoGoogleAdapter = localDemoGoogleAdapter;
    }

    public record UserInfoRow(
        UUID id,
        String demoRole,
        String displayName,
        String email,
        String phone,
        String status
    ) {}

    public OAuthProviderConfigResponse getGoogleConfig() {
        boolean isLive = googleOAuthAdapter.isAvailable();
        return new OAuthProviderConfigResponse(
            "GOOGLE",
            isLive ? "LIVE" : "DEMO",
            isLive ? googleOAuthAdapter.getClientId() : "",
            LocalDemoGoogleAdapter.DEMO_ACCOUNTS
        );
    }

    @Transactional
    public AuthResponse processGoogleCallback(OAuthCallbackRequest request, HttpServletResponse response) {
        if (request == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Dữ liệu xác thực không hợp lệ");
        }

        // Determine adapter: if demoAccountId is specified or token is demo prefix or google is unavailable
        OAuthIdentityPort port;
        boolean isDemoRequest = (request.demoAccountId() != null && !request.demoAccountId().isBlank())
            || (request.idToken() != null && (request.idToken().startsWith("demo-") || request.idToken().startsWith("mock-")));

        if (isDemoRequest || !googleOAuthAdapter.isAvailable()) {
            port = localDemoGoogleAdapter;
        } else {
            port = googleOAuthAdapter;
        }

        OAuthUserInfo userInfo = port.authenticate(request);
        if (userInfo == null || userInfo.email() == null || userInfo.email().isBlank()) {
            throw new ApiException(401, "OAUTH_FAILED", "Không thể xác định danh tính từ nhà cung cấp OAuth");
        }

        String normalizedEmail = userInfo.email().trim().toLowerCase();
        UUID userId;
        String displayName = userInfo.name() != null && !userInfo.name().isBlank() ? userInfo.name().trim() : normalizedEmail.split("@")[0];
        String phone = "";
        String status = "ACTIVE";
        String demoRole = "CUSTOMER";

        // 1. Check existing oauth_identities
        var oauthRows = jdbc.query(
            "SELECT user_id FROM ticketing.oauth_identities WHERE provider = 'GOOGLE' AND provider_user_id = ?",
            (rs, rowNum) -> rs.getObject("user_id", UUID.class),
            userInfo.providerUserId()
        );

        if (!oauthRows.isEmpty()) {
            userId = oauthRows.getFirst();
            // Update profile info
            jdbc.update(
                "UPDATE ticketing.oauth_identities SET display_name = ?, avatar_url = ?, updated_at = clock_timestamp() WHERE provider = 'GOOGLE' AND provider_user_id = ?",
                displayName, userInfo.avatarUrl(), userInfo.providerUserId()
            );

            // Fetch user info & check status
            var users = jdbc.query(
                "SELECT id, demo_role, display_name, email, phone, status FROM ticketing.users WHERE id = ?",
                (rs, rowNum) -> new UserInfoRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("demo_role"),
                    rs.getString("display_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("status")
                ),
                userId
            );

            if (users.isEmpty()) {
                throw new ApiException(401, "UNAUTHENTICATED", "Tài khoản liên kết không tồn tại");
            }

            UserInfoRow user = users.getFirst();
            demoRole = user.demoRole();
            displayName = user.displayName();
            phone = user.phone() != null ? user.phone() : "";
            status = user.status() != null ? user.status() : "ACTIVE";

        } else {
            // 2. Check if users already has this email
            var existingUsers = jdbc.query(
                "SELECT id, demo_role, display_name, email, phone, status FROM ticketing.users WHERE LOWER(email) = ?",
                (rs, rowNum) -> new UserInfoRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("demo_role"),
                    rs.getString("display_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("status")
                ),
                normalizedEmail
            );

            if (!existingUsers.isEmpty()) {
                UserInfoRow user = existingUsers.getFirst();
                userId = user.id();
                demoRole = user.demoRole();
                displayName = user.displayName();
                phone = user.phone() != null ? user.phone() : "";
                status = user.status() != null ? user.status() : "ACTIVE";

                // Link to oauth_identities
                jdbc.update(
                    "INSERT INTO ticketing.oauth_identities (user_id, provider, provider_user_id, email, display_name, avatar_url) VALUES (?, 'GOOGLE', ?, ?, ?, ?) ON CONFLICT (provider, provider_user_id) DO UPDATE SET updated_at = clock_timestamp()",
                    userId, userInfo.providerUserId(), normalizedEmail, displayName, userInfo.avatarUrl()
                );
            } else {
                // 3. New user registration
                userId = UUID.randomUUID();
                jdbc.update(
                    "INSERT INTO ticketing.users (id, demo_role, display_name, email, password_hash, phone, status) VALUES (?, 'CUSTOMER', ?, ?, ?, '', 'ACTIVE')",
                    userId, displayName, normalizedEmail, "$2a$10$OAUTH2_PASSWORDLESS_SECURE_TOKEN_PLACEHOLDER"
                );
                jdbc.update(
                    "INSERT INTO ticketing.user_roles (user_id, role_id) VALUES (?, 'CUSTOMER') ON CONFLICT DO NOTHING",
                    userId
                );
                jdbc.update(
                    "INSERT INTO ticketing.oauth_identities (user_id, provider, provider_user_id, email, display_name, avatar_url) VALUES (?, 'GOOGLE', ?, ?, ?, ?)",
                    userId, userInfo.providerUserId(), normalizedEmail, displayName, userInfo.avatarUrl()
                );
            }
        }

        // Anti-scalping / Security Check: Block login if account is LOCKED
        if ("LOCKED".equalsIgnoreCase(status)) {
            throw new ApiException(403, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa do vi phạm chính sách hoặc phát hiện hành vi đầu cơ bất thường");
        }

        List<String> roles = fetchUserRoles(userId, demoRole);
        String primaryRole = !roles.isEmpty() ? roles.getFirst() : "CUSTOMER";

        String accessToken = jwtService.generateToken(userId, normalizedEmail, primaryRole, roles, displayName);
        String refreshToken = refreshTokenService.createRefreshToken(userId);

        if (response != null) {
            setRefreshCookie(response, refreshToken, 7 * 24 * 3600);
        }

        UserDto userDto = new UserDto(userId, displayName, normalizedEmail, phone, primaryRole, roles);
        return new AuthResponse(accessToken, userDto);
    }

    private List<String> fetchUserRoles(UUID userId, String fallbackRole) {
        List<String> roles = Collections.emptyList();
        try {
            roles = jdbc.query(
                "SELECT role_id FROM ticketing.user_roles WHERE user_id = ? ORDER BY role_id",
                (rs, rowNum) -> rs.getString("role_id"),
                userId
            );
        } catch (Exception ignored) {}
        if (roles.isEmpty() && fallbackRole != null) {
            return List.of(fallbackRole);
        }
        return roles.isEmpty() ? List.of("CUSTOMER") : roles;
    }

    private void setRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(false)
            .path("/")
            .maxAge(maxAgeSeconds)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
