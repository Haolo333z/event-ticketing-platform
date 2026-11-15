package com.haolo.ticketing.identity;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/admin/leads")
public class AdminLeadController {
    private final JdbcTemplate jdbc;

    public AdminLeadController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ContactLead(
        UUID id,
        String fullName,
        String email,
        String phone,
        String companyName,
        String eventScale,
        String message,
        String createdAt
    ) {}

    public record Page<T>(List<T> items, int page, int size, int totalElements) {}

    @GetMapping
    public ResponseEntity<Page<ContactLead>> listLeads(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ");
        }

        Integer total = jdbc.queryForObject("SELECT count(*) FROM ticketing.contact_leads", Integer.class);
        int totalElements = total != null ? total : 0;

        List<ContactLead> items = jdbc.query(
            "SELECT id, full_name, email, phone, company_name, event_scale, message, created_at FROM ticketing.contact_leads ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (rs, rowNum) -> new ContactLead(
                rs.getObject("id", UUID.class),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("company_name") != null ? rs.getString("company_name") : "",
                rs.getString("event_scale") != null ? rs.getString("event_scale") : "",
                rs.getString("message") != null ? rs.getString("message") : "",
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant().toString() : ""
            ),
            size, page * size
        );

        return ResponseEntity.ok(new Page<>(items, page, size, totalElements));
    }
}
