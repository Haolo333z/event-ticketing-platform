package com.haolo.ticketing.identity;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/admin/leads")
@PreAuthorize("hasRole('ADMIN')")
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
        String status,
        String notes,
        String createdAt,
        String updatedAt
    ) {}

    public record Page<T>(List<T> items, int page, int size, int totalElements) {}

    public record UpdateLeadStatusRequest(String status, String notes) {}

    @GetMapping
    public ResponseEntity<Page<ContactLead>> listLeads(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ");
        }

        String countSql = "SELECT count(*) FROM ticketing.contact_leads";
        String dataSql = "SELECT id, full_name, email, phone, company_name, event_scale, message, status, notes, created_at, updated_at FROM ticketing.contact_leads";
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            String where = " WHERE status = ?";
            countSql += where;
            dataSql += where;
            params.add(status.trim().toUpperCase());
        }

        Integer total = jdbc.queryForObject(countSql, Integer.class, params.toArray());
        int totalElements = total != null ? total : 0;

        dataSql += " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add(page * size);

        List<ContactLead> items = jdbc.query(dataSql, (rs, rowNum) -> {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            return new ContactLead(
                rs.getObject("id", UUID.class),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("company_name") != null ? rs.getString("company_name") : "",
                rs.getString("event_scale") != null ? rs.getString("event_scale") : "",
                rs.getString("message") != null ? rs.getString("message") : "",
                rs.getString("status") != null ? rs.getString("status") : "NEW",
                rs.getString("notes"),
                createdAt != null ? createdAt.toInstant().toString() : null,
                updatedAt != null ? updatedAt.toInstant().toString() : null
            );
        }, queryParams.toArray());

        return ResponseEntity.ok(new Page<>(items, page, size, totalElements));
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<ContactLead> updateStatus(
        @PathVariable UUID id,
        @RequestBody UpdateLeadStatusRequest req,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        if (principal == null || !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ quản trị viên mới có quyền cập nhật trạng thái");
        }
        if (req == null || req.status() == null || req.status().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thiếu trạng thái lead");
        }

        String status = req.status().trim().toUpperCase();
        if (!Set.of("NEW", "CONTACTED", "CONVERTED", "CLOSED").contains(status)) {
            throw new ApiException(400, "INVALID_STATUS", "Trạng thái không hợp lệ: " + status + " (hỗ trợ NEW, CONTACTED, CONVERTED, CLOSED)");
        }

        int updated = jdbc.update(
            "UPDATE ticketing.contact_leads SET status = ?, notes = COALESCE(?, notes), updated_at = clock_timestamp() WHERE id = ?",
            status, req.notes(), id
        );

        if (updated == 0) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy lead yêu cầu tư vấn");
        }

        // Audit log
        jdbc.update(
            "INSERT INTO ticketing.audit_logs (actor_id, actor_role, action, target_type, target_id, payload_diff) " +
            "VALUES (?, 'ADMIN', 'UPDATE_LEAD_STATUS', 'CONTACT_LEAD', ?, ?::jsonb)",
            principal.accountId(),
            id.toString(),
            "{\"status\":\"" + status + "\",\"notes\":\"" + (req.notes() != null ? req.notes().replace("\"", "\\\"") : "") + "\"}"
        );

        var rows = jdbc.queryForList(
            "SELECT id, full_name, email, phone, company_name, event_scale, message, status, notes, created_at, updated_at FROM ticketing.contact_leads WHERE id = ?",
            id
        );
        var r = rows.getFirst();
        Timestamp createdAt = (Timestamp) r.get("created_at");
        Timestamp updatedAt = (Timestamp) r.get("updated_at");

        return ResponseEntity.ok(new ContactLead(
            (UUID) r.get("id"),
            (String) r.get("full_name"),
            (String) r.get("email"),
            (String) r.get("phone"),
            (String) r.get("company_name"),
            (String) r.get("event_scale"),
            (String) r.get("message"),
            (String) r.get("status"),
            (String) r.get("notes"),
            createdAt != null ? createdAt.toInstant().toString() : null,
            updatedAt != null ? updatedAt.toInstant().toString() : null
        ));
    }
}
