package com.haolo.ticketing.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final JdbcTemplate jdbc;

    public AdminUserController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UserSummary(
        UUID id,
        String displayName,
        String email,
        String phone,
        String role,
        List<String> roles,
        String status,
        String createdAt
    ) {}

    public record Page<T>(List<T> items, int page, int size, int totalElements) {}

    public record UpdateRoleRequest(
        String role,
        String action // "GRANT" or "REVOKE"
    ) {}

    public record UpdateStatusRequest(
        String status // "ACTIVE" or "LOCKED"
    ) {}

    @GetMapping
    public ResponseEntity<Page<UserSummary>> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String role
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ");
        }

        String countSql = "SELECT count(DISTINCT u.id) FROM ticketing.users u";
        String dataSql = "SELECT u.id, u.display_name, u.email, u.phone, u.demo_role, u.status, u.created_at FROM ticketing.users u";
        List<String> whereClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (role != null && !role.isBlank()) {
            String roleUpper = role.trim().toUpperCase();
            countSql += " JOIN ticketing.user_roles ur_filter ON ur_filter.user_id = u.id AND ur_filter.role_id = ?";
            dataSql += " JOIN ticketing.user_roles ur_filter ON ur_filter.user_id = u.id AND ur_filter.role_id = ?";
            params.add(roleUpper);
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            whereClauses.add("(LOWER(u.display_name) LIKE ? OR LOWER(COALESCE(u.email, '')) LIKE ? OR COALESCE(u.phone, '') LIKE ?)");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        if (!whereClauses.isEmpty()) {
            String where = " WHERE " + String.join(" AND ", whereClauses);
            countSql += where;
            dataSql += where;
        }

        Integer total = jdbc.queryForObject(countSql, Integer.class, params.toArray());
        int totalElements = total != null ? total : 0;

        dataSql += " ORDER BY u.created_at DESC LIMIT ? OFFSET ?";
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add(page * size);

        List<UserSummary> items = jdbc.query(dataSql, (rs, rowNum) -> {
            UUID userId = rs.getObject("id", UUID.class);
            List<String> userRoles = jdbc.queryForList(
                "SELECT role_id FROM ticketing.user_roles WHERE user_id = ? ORDER BY role_id",
                String.class,
                userId
            );
            if (userRoles.isEmpty() && rs.getString("demo_role") != null) {
                userRoles = List.of(rs.getString("demo_role"));
            }
            String st = rs.getString("status");
            return new UserSummary(
                userId,
                rs.getString("display_name"),
                rs.getString("email") != null ? rs.getString("email") : "",
                rs.getString("phone") != null ? rs.getString("phone") : "",
                rs.getString("demo_role"),
                userRoles,
                st != null ? st : "ACTIVE",
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant().toString() : ""
            );
        }, queryParams.toArray());

        return ResponseEntity.ok(new Page<>(items, page, size, totalElements));
    }

    @PostMapping("/{id}/roles")
    @Transactional
    public ResponseEntity<UserSummary> updateUserRole(
        @PathVariable UUID id,
        @RequestBody UpdateRoleRequest req,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        if (principal == null || !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ quản trị viên mới có quyền cập nhật vai trò");
        }
        if (req == null || req.role() == null || req.action() == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thiếu thông tin vai trò hoặc hành động");
        }

        String role = req.role().trim().toUpperCase();
        String action = req.action().trim().toUpperCase();

        if (!Set.of("CUSTOMER", "STAFF", "ORGANIZER", "ADMIN").contains(role)) {
            throw new ApiException(400, "INVALID_ROLE", "Vai trò không hợp lệ: " + role);
        }

        // Self-lockout prevention
        if (principal.accountId() != null && principal.accountId().equals(id) && "ADMIN".equals(role) && "REVOKE".equals(action)) {
            throw new ApiException(400, "CANNOT_DEMOTE_SELF", "Không thể tự tước quyền Quản trị viên của chính mình!");
        }

        // Check if user exists
        var userRows = jdbc.queryForList("SELECT id, display_name, email, phone, demo_role, status, created_at FROM ticketing.users WHERE id = ?", id);
        if (userRows.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy người dùng");
        }

        if ("GRANT".equals(action)) {
            jdbc.update("INSERT INTO ticketing.user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING", id, role);
            jdbc.update("UPDATE ticketing.users SET demo_role = ? WHERE id = ? AND (demo_role = 'CUSTOMER' OR demo_role IS NULL)", role, id);
        } else if ("REVOKE".equals(action)) {
            jdbc.update("DELETE FROM ticketing.user_roles WHERE user_id = ? AND role_id = ?", id, role);
            // If demo_role was revoked, update to remaining role or CUSTOMER
            List<String> remaining = jdbc.queryForList("SELECT role_id FROM ticketing.user_roles WHERE user_id = ? LIMIT 1", String.class, id);
            String newDemoRole = remaining.isEmpty() ? "CUSTOMER" : remaining.get(0);
            jdbc.update("UPDATE ticketing.users SET demo_role = ? WHERE id = ?", newDemoRole, id);
        } else {
            throw new ApiException(400, "INVALID_ACTION", "Hành động không hợp lệ: " + action + " (hỗ trợ GRANT, REVOKE)");
        }

        // Audit log
        jdbc.update(
            "INSERT INTO ticketing.audit_logs (actor_id, actor_role, action, target_type, target_id, payload_diff) " +
            "VALUES (?, 'ADMIN', ?, 'USER_ROLE', ?, ?::jsonb)",
            principal.accountId(),
            "ROLE_" + action,
            id.toString(),
            "{\"role\":\"" + role + "\",\"action\":\"" + action + "\"}"
        );

        return ResponseEntity.ok(getUserSummary(id));
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<UserSummary> updateUserStatus(
        @PathVariable UUID id,
        @RequestBody UpdateStatusRequest req,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        if (principal == null || !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ quản trị viên mới có quyền cập nhật trạng thái");
        }
        if (req == null || req.status() == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thiếu trạng thái người dùng");
        }

        String status = req.status().trim().toUpperCase();
        if (!Set.of("ACTIVE", "LOCKED").contains(status)) {
            throw new ApiException(400, "INVALID_STATUS", "Trạng thái không hợp lệ: " + status + " (hỗ trợ ACTIVE, LOCKED)");
        }

        // Self-lockout prevention
        if (principal.accountId() != null && principal.accountId().equals(id) && "LOCKED".equals(status)) {
            throw new ApiException(400, "CANNOT_LOCK_SELF", "Không thể tự khóa tài khoản của chính mình!");
        }

        int updated = jdbc.update("UPDATE ticketing.users SET status = ? WHERE id = ?", status, id);
        if (updated == 0) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy người dùng");
        }

        // Audit log
        jdbc.update(
            "INSERT INTO ticketing.audit_logs (actor_id, actor_role, action, target_type, target_id, payload_diff) " +
            "VALUES (?, 'ADMIN', 'UPDATE_STATUS', 'USER', ?, ?::jsonb)",
            principal.accountId(),
            id.toString(),
            "{\"status\":\"" + status + "\"}"
        );

        return ResponseEntity.ok(getUserSummary(id));
    }

    private UserSummary getUserSummary(UUID userId) {
        var rows = jdbc.queryForList("SELECT id, display_name, email, phone, demo_role, status, created_at FROM ticketing.users WHERE id = ?", userId);
        if (rows.isEmpty()) throw new ApiException(404, "NOT_FOUND", "Không tìm thấy người dùng");
        var r = rows.getFirst();
        List<String> roles = jdbc.queryForList("SELECT role_id FROM ticketing.user_roles WHERE user_id = ? ORDER BY role_id", String.class, userId);
        String st = (String) r.get("status");
        return new UserSummary(
            (UUID) r.get("id"),
            (String) r.get("display_name"),
            r.get("email") != null ? (String) r.get("email") : "",
            r.get("phone") != null ? (String) r.get("phone") : "",
            (String) r.get("demo_role"),
            roles,
            st != null ? st : "ACTIVE",
            r.get("created_at") != null ? r.get("created_at").toString() : ""
        );
    }
}
