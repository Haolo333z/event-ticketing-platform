package com.haolo.ticketing.identity;

import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record RegisterRequest(String displayName, String email, String password, String phone) {}
    public record LoginRequest(String email, String password) {}
    public record UserDto(UUID id, String displayName, String email, String phone, String role) {}
    public record AuthResponse(String token, UserDto user) {}

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req) {
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
            jdbc.update("INSERT INTO ticketing.users(id, demo_role, display_name, email, password_hash, phone) VALUES (?, 'CUSTOMER', ?, ?, ?, ?)",
                id, displayName, normalizedEmail, passwordHash, phone);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(400, "DUPLICATE_EMAIL", "Email này đã được sử dụng");
        }

        String token = jwtService.generateToken(id, normalizedEmail, "CUSTOMER", displayName);
        UserDto userDto = new UserDto(id, displayName, normalizedEmail, phone, "CUSTOMER");
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, userDto));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {
        if (req == null || req.email() == null || req.email().isBlank() || req.password() == null || req.password().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Email và mật khẩu không được để trống");
        }

        String normalizedEmail = req.email().trim().toLowerCase();
        var users = jdbc.query(
            "SELECT id, demo_role, display_name, email, password_hash, phone FROM ticketing.users WHERE LOWER(email) = ?",
            (rs, rowNum) -> new Object[] {
                rs.getObject("id", UUID.class),
                rs.getString("demo_role"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("phone")
            },
            normalizedEmail
        );

        if (users.isEmpty()) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác");
        }

        Object[] userRow = users.getFirst();
        UUID id = (UUID) userRow[0];
        String role = (String) userRow[1];
        String displayName = (String) userRow[2];
        String email = (String) userRow[3];
        String passwordHash = (String) userRow[4];
        String phone = (String) userRow[5];

        if (passwordHash == null || !passwordEncoder.matches(req.password(), passwordHash)) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác");
        }

        String token = jwtService.generateToken(id, email, role, displayName);
        UserDto userDto = new UserDto(id, displayName, email, phone, role);
        return ResponseEntity.ok(new AuthResponse(token, userDto));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal DemoPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Cần đăng nhập");
        }

        var users = jdbc.query(
            "SELECT id, demo_role, display_name, email, phone FROM ticketing.users WHERE id = ?",
            (rs, rowNum) -> new UserDto(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("demo_role")
            ),
            principal.accountId()
        );

        if (users.isEmpty()) {
            throw new ApiException(401, "UNAUTHENTICATED", "Tài khoản không tồn tại");
        }

        return ResponseEntity.ok(users.getFirst());
    }
}
