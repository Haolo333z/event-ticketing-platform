package com.haolo.ticketing.organizer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/organizer")
public class OrganizerOnboardingController {

    private final JdbcTemplate jdbc;

    public OrganizerOnboardingController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record OrganizerOnboardRequest(
        String organizationName,
        String contactEmail,
        String phone,
        String taxCode,
        String saasPlan,
        String message
    ) {}

    public record OrganizerOnboardResponse(
        UUID leadId,
        String organizationName,
        String contactEmail,
        String saasPlan,
        String status,
        String message
    ) {}

    public record OnboardingStatusDto(
        boolean hasSubmitted,
        String status,
        String statusDisplay,
        String organizationName,
        String contactEmail,
        String saasPlan,
        String notes,
        Instant submittedAt
    ) {}

    @GetMapping("/onboard/status")
    public ResponseEntity<OnboardingStatusDto> getOnboardingStatus(@AuthenticationPrincipal DemoPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.ok(new OnboardingStatusDto(false, null, null, null, null, null, null, null));
        }

        UUID userId = principal.accountId();
        String userEmail = null;
        try {
            userEmail = jdbc.queryForObject("SELECT email FROM ticketing.users WHERE id = ?", String.class, userId);
        } catch (Exception ignored) {}

        var rows = jdbc.queryForList(
            "SELECT id, company_name, email, saas_plan, status, notes, created_at FROM ticketing.contact_leads " +
            "WHERE user_id = ? OR email = ? " +
            "ORDER BY created_at DESC LIMIT 1",
            userId, (userEmail != null ? userEmail : "")
        );

        if (rows.isEmpty()) {
            return ResponseEntity.ok(new OnboardingStatusDto(false, null, null, null, null, null, null, null));
        }

        var r = rows.getFirst();
        String status = (String) r.get("status");
        String company = (String) r.get("company_name");
        String email = (String) r.get("email");
        String plan = (String) r.get("saas_plan");
        String notes = (String) r.get("notes");
        Timestamp createdAt = (Timestamp) r.get("created_at");

        String statusDisplay = switch (status != null ? status.toUpperCase() : "NEW") {
            case "NEW", "CONTACTED" -> "Đang chờ duyệt";
            case "CONVERTED" -> "Đã duyệt";
            case "REJECTED", "CLOSED" -> "Bị từ chối";
            default -> "Đang chờ duyệt";
        };

        return ResponseEntity.ok(new OnboardingStatusDto(
            true,
            status,
            statusDisplay,
            company,
            email,
            plan,
            notes,
            createdAt != null ? createdAt.toInstant() : null
        ));
    }

    public ResponseEntity<OrganizerOnboardResponse> submitOnboardRequest(OrganizerOnboardRequest req) {
        return submitOnboardRequest(req, null);
    }

    @PostMapping("/onboard")
    public ResponseEntity<OrganizerOnboardResponse> submitOnboardRequest(
        @RequestBody OrganizerOnboardRequest req,
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        if (req == null) throw new ApiException(400, "VALIDATION_ERROR", "Dữ liệu không được để trống");
        if (req.organizationName() == null || req.organizationName().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tên tổ chức/doanh nghiệp không được để trống");
        }
        if (req.phone() == null || req.phone().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Số điện thoại không được để trống");
        }

        String email;
        UUID userId = null;

        if (principal != null) {
            userId = principal.accountId();
            // Locked email from authenticated user
            try {
                email = jdbc.queryForObject("SELECT email FROM ticketing.users WHERE id = ?", String.class, userId);
            } catch (Exception ex) {
                email = req.contactEmail() != null ? req.contactEmail().trim().toLowerCase() : null;
            }

            // Check if already has a pending application
            Integer pendingCount = jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.contact_leads WHERE (user_id = ? OR email = ?) AND status IN ('NEW', 'CONTACTED')",
                Integer.class,
                userId, email
            );
            if (pendingCount != null && pendingCount > 0) {
                throw new ApiException(409, "ONBOARDING_ALREADY_PENDING", "Hồ sơ đăng ký của bạn đang trong quá trình xét duyệt, không thể gửi trùng!");
            }
        } else {
            if (req.contactEmail() == null || req.contactEmail().isBlank() || !req.contactEmail().contains("@")) {
                throw new ApiException(400, "VALIDATION_ERROR", "Email liên hệ không hợp lệ");
            }
            email = req.contactEmail().trim().toLowerCase();
        }

        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new ApiException(400, "VALIDATION_ERROR", "Email không hợp lệ");
        }

        String plan = req.saasPlan() != null ? req.saasPlan().trim().toUpperCase() : "PROFESSIONAL";
        if (!Set.of("STARTER", "PROFESSIONAL", "ENTERPRISE").contains(plan)) {
            plan = "PROFESSIONAL";
        }

        UUID leadId = UUID.randomUUID();
        String company = req.organizationName().trim();
        String phone = req.phone().trim();
        String tax = req.taxCode() != null ? req.taxCode().trim() : "";
        String msg = req.message() != null ? req.message().trim() : "";
        if (!tax.isBlank()) {
            msg = "[MST: " + tax + "] " + msg;
        }

        jdbc.update(
            "INSERT INTO ticketing.contact_leads (id, user_id, full_name, email, phone, company_name, event_scale, saas_plan, message, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NEW')",
            leadId, userId, company, email, phone, company, plan, plan, msg
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new OrganizerOnboardResponse(
            leadId,
            company,
            email,
            plan,
            "NEW",
            "Đăng ký thành công! Đội ngũ EventFlow Enterprise sẽ liên hệ xác thực và kích hoạt tài khoản trong vòng 24 giờ."
        ));
    }
}
