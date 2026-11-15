package com.haolo.ticketing.identity;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/admin/users")
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
        String createdAt
    ) {}

    public record Page<T>(List<T> items, int page, int size, int totalElements) {}

    @GetMapping
    public ResponseEntity<Page<UserSummary>> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ");
        }

        String countSql = "SELECT count(*) FROM ticketing.users";
        String dataSql = "SELECT id, display_name, email, phone, demo_role, created_at FROM ticketing.users";
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            String where = " WHERE LOWER(display_name) LIKE ? OR LOWER(COALESCE(email, '')) LIKE ? OR COALESCE(phone, '') LIKE ?";
            countSql += where;
            dataSql += where;
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        Integer total = jdbc.queryForObject(countSql, Integer.class, params.toArray());
        int totalElements = total != null ? total : 0;

        dataSql += " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add(page * size);

        List<UserSummary> items = jdbc.query(dataSql, (rs, rowNum) -> new UserSummary(
            rs.getObject("id", UUID.class),
            rs.getString("display_name"),
            rs.getString("email") != null ? rs.getString("email") : "",
            rs.getString("phone") != null ? rs.getString("phone") : "",
            rs.getString("demo_role"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant().toString() : ""
        ), queryParams.toArray());

        return ResponseEntity.ok(new Page<>(items, page, size, totalElements));
    }
}
