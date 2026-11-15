package com.haolo.ticketing.identity;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/contact")
public class ContactLeadController {
    private final JdbcTemplate jdbc;

    public ContactLeadController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LeadRequest(
        String fullName,
        String email,
        String phone,
        String companyName,
        String eventScale,
        String message
    ) {}

    public record LeadResponse(UUID id, String message) {}

    @PostMapping("/leads")
    public ResponseEntity<LeadResponse> submitLead(@RequestBody LeadRequest req) {
        if (req == null || req.fullName() == null || req.fullName().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Họ tên không được để trống");
        }
        if (req.email() == null || req.email().isBlank() || !req.email().contains("@")) {
            throw new ApiException(400, "VALIDATION_ERROR", "Email không hợp lệ");
        }
        if (req.phone() == null || req.phone().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Số điện thoại không được để trống");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ticketing.contact_leads(id, full_name, email, phone, company_name, event_scale, message) VALUES (?, ?, ?, ?, ?, ?, ?)",
            id, req.fullName().trim(), req.email().trim().toLowerCase(), req.phone().trim(),
            req.companyName() != null ? req.companyName().trim() : "",
            req.eventScale() != null ? req.eventScale().trim() : "",
            req.message() != null ? req.message().trim() : ""
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            new LeadResponse(id, "Thông tin liên hệ đã được ghi nhận. Đội ngũ EventFlow sẽ liên hệ trong 15 phút.")
        );
    }
}
