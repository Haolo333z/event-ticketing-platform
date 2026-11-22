package com.haolo.ticketing.identity;

import java.util.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String REFRESH_COOKIE_NAME = "eventflow_refresh";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
        JdbcTemplate jdbc,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenService refreshTokenService
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public record RegisterRequest(String displayName, String email, String password, String phone) {}
    public record LoginRequest(String email, String password) {}
    public record UserDto(UUID id, String displayName, String email, String phone, String role, List<String> roles) {
        public UserDto(UUID id, String displayName, String email, String phone, String role) {
            this(id, displayName, email, phone, role, role != null ? List.of(role) : List.of("CUSTOMER"));
        }
    }
    public record AuthResponse(String token, UserDto user) {}

    record UserDbRow(UUID id, String demoRole, String displayName, String email, String passwordHash, String phone) {}
    record UserMeRow(UUID id, String demoRole, String displayName, String email, String phone) {}

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req, HttpServletRequest request, HttpServletResponse response) {
        if (req == null || req.email() == null || req.email().isBlank() || !req.email().contains("@")) {
            throw new ApiException(400, "VALIDATION_ERROR", "Email không hợp lệ");
        }
        if (req.displayName() == null || req.displayName().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Họ tên không được để trống");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mật khẩu phải có tối thiểu 6 ký tự");
        }

        String normalizedEmail = req.email().trim().toLowerCase();
        Integer existingCount = jdbc.queryForObject("SELECT count(*) FROM ticketing.users WHERE LOWER(email) = ?", Integer.class, normalizedEmail);
        if (existingCount != null && existingCount > 0) {
            throw new ApiException(400, "DUPLICATE_EMAIL", "Email này đã được sử dụng");
        }

        UUID id = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(req.password());
        String displayName = req.displayName().trim();
        String phone = req.phone() != null ? req.phone().trim() : "";

        try {
            jdbc.update(
                "INSERT INTO ticketing.users(id, demo_role, display_name, email, password_hash, phone) VALUES (?, 'CUSTOMER', ?, ?, ?, ?)",
                id, displayName, normalizedEmail, passwordHash, phone
            );
            jdbc.update(
                "INSERT INTO ticketing.user_roles(user_id, role_id) VALUES (?, 'CUSTOMER') ON CONFLICT DO NOTHING",
                id
            );
        } catch (DuplicateKeyException ex) {
            throw new ApiException(400, "DUPLICATE_EMAIL", "Email này đã được sử dụng");
        }

        List<String> roles = List.of("CUSTOMER");
        String accessToken = jwtService.generateToken(id, normalizedEmail, "CUSTOMER", roles, displayName);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String ipAddress = request != null ? request.getRemoteAddr() : null;
        String refreshToken = (userAgent != null || ipAddress != null)
            ? refreshTokenService.createRefreshToken(id, userAgent, ipAddress)
            : refreshTokenService.createRefreshToken(id);

        setRefreshCookie(response, refreshToken, 7 * 24 * 3600);

        UserDto userDto = new UserDto(id, displayName, normalizedEmail, phone, "CUSTOMER", roles);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(accessToken, userDto));
    }

    public ResponseEntity<AuthResponse> register(RegisterRequest req, HttpServletResponse response) {
        return register(req, null, response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        if (req == null || req.email() == null || req.email().isBlank() || req.password() == null || req.password().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Email và mật khẩu không được để trống");
        }

        String normalizedEmail = req.email().trim().toLowerCase();
        var users = jdbc.query(
            "SELECT id, demo_role, display_name, email, password_hash, phone FROM ticketing.users WHERE LOWER(email) = ?",
            (rs, rowNum) -> new UserDbRow(
                rs.getObject("id", UUID.class),
                rs.getString("demo_role"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("phone")
            ),
            normalizedEmail
        );

        if (users.isEmpty()) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác");
        }

        UserDbRow user = users.getFirst();
        UUID id = user.id();
        String demoRole = user.demoRole();
        String displayName = user.displayName();
        String email = user.email();
        String passwordHash = user.passwordHash();
        String phone = user.phone();

        if (passwordHash == null || !passwordEncoder.matches(req.password(), passwordHash)) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác");
        }

        List<String> roles = fetchUserRoles(id, demoRole);
        List<String> priorityOrder = List.of("ADMIN", "ORGANIZER", "STAFF", "CUSTOMER");
        String primaryRole = priorityOrder.stream().filter(roles::contains).findFirst().orElse(!roles.isEmpty() ? roles.getFirst() : (demoRole != null ? demoRole : "CUSTOMER"));

        String accessToken = jwtService.generateToken(id, email, primaryRole, roles, displayName);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String ipAddress = request != null ? request.getRemoteAddr() : null;
        String refreshToken = (userAgent != null || ipAddress != null)
            ? refreshTokenService.createRefreshToken(id, userAgent, ipAddress)
            : refreshTokenService.createRefreshToken(id);

        setRefreshCookie(response, refreshToken, 7 * 24 * 3600);

        UserDto userDto = new UserDto(id, displayName, email, phone, primaryRole, roles);
        return ResponseEntity.ok(new AuthResponse(accessToken, userDto));
    }

    public ResponseEntity<AuthResponse> login(LoginRequest req, HttpServletResponse response) {
        return login(req, null, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(401, "UNAUTHENTICATED", "Không tìm thấy phiên làm việc (Refresh Cookie). Vui lòng đăng nhập lại.");
        }

        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String ipAddress = request != null ? request.getRemoteAddr() : null;
        var rotation = (userAgent != null || ipAddress != null)
            ? refreshTokenService.rotateRefreshToken(rawRefreshToken, userAgent, ipAddress)
            : refreshTokenService.rotateRefreshToken(rawRefreshToken);
        UUID userId = rotation.userId();

        var users = jdbc.query(
            "SELECT id, demo_role, display_name, email, phone FROM ticketing.users WHERE id = ?",
            (rs, rowNum) -> new UserMeRow(
                rs.getObject("id", UUID.class),
                rs.getString("demo_role"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("phone")
            ),
            userId
        );

        if (users.isEmpty()) {
            throw new ApiException(401, "UNAUTHENTICATED", "Tài khoản không tồn tại");
        }

        UserMeRow user = users.getFirst();
        String demoRole = user.demoRole();
        String displayName = user.displayName();
        String email = user.email();
        String phone = user.phone();

        List<String> roles = fetchUserRoles(userId, demoRole);
        List<String> priorityOrder = List.of("ADMIN", "ORGANIZER", "STAFF", "CUSTOMER");
        String primaryRole = priorityOrder.stream().filter(roles::contains).findFirst().orElse(!roles.isEmpty() ? roles.getFirst() : (demoRole != null ? demoRole : "CUSTOMER"));

        String newAccessToken = jwtService.generateToken(userId, email, primaryRole, roles, displayName);
        setRefreshCookie(response, rotation.newRawToken(), 7 * 24 * 3600);

        UserDto userDto = new UserDto(userId, displayName, email, phone, primaryRole, roles);
        return ResponseEntity.ok(new AuthResponse(newAccessToken, userDto));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeByRawToken(rawRefreshToken);
        }

        // Clear cookie
        setRefreshCookie(response, "", 0);
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal DemoPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        }

        var users = jdbc.query(
            "SELECT id, demo_role, display_name, email, phone FROM ticketing.users WHERE id = ?",
            (rs, rowNum) -> new UserMeRow(
                rs.getObject("id", UUID.class),
                rs.getString("demo_role"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("phone")
            ),
            principal.accountId()
        );

        if (users.isEmpty()) {
            throw new ApiException(401, "UNAUTHENTICATED", "Tài khoản không tồn tại");
        }

        UserMeRow row = users.getFirst();
        UUID id = row.id();
        String demoRole = row.demoRole();
        String displayName = row.displayName();
        String email = row.email();
        String phone = row.phone();

        List<String> roles = fetchUserRoles(id, demoRole);
        List<String> priorityOrder = List.of("ADMIN", "ORGANIZER", "STAFF", "CUSTOMER");
        String primaryRole = priorityOrder.stream().filter(roles::contains).findFirst().orElse(!roles.isEmpty() ? roles.getFirst() : (demoRole != null ? demoRole : "CUSTOMER"));

        return ResponseEntity.ok(new UserDto(id, displayName, email, phone, primaryRole, roles));
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
            // fallback if table does not exist yet
        }
        if (roles.isEmpty() && fallbackRole != null) {
            return List.of(fallbackRole);
        }
        return roles.isEmpty() ? List.of("CUSTOMER") : roles;
    }

    private void setRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(false) // set to true in production SSL; false allows localhost HTTP development
            .path("/")
            .maxAge(maxAgeSeconds)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
