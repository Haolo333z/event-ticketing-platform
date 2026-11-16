package com.haolo.ticketing.admin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.admin.AdminModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlatformController {

    private final AdminRefundService refundService;
    private final AdminReportService reportService;

    public AdminPlatformController(
            AdminRefundService refundService,
            AdminReportService reportService) {
        this.refundService = refundService;
        this.reportService = reportService;
    }

    // --- Refunds ---
    @GetMapping("/refunds")
    public ResponseEntity<List<RefundItemDto>> listRefunds(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(refundService.listRefunds(status));
    }

    @PostMapping("/refunds/{id}/process")
    public ResponseEntity<ProcessRefundResult> processRefund(
            @PathVariable UUID id,
            @RequestBody ProcessRefundRequest req,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return ResponseEntity.ok(refundService.processRefund(id, req, principal));
    }

    // --- Reports ---
    @GetMapping("/reports/summary")
    public ResponseEntity<PlatformSummaryDto> getPlatformSummary() {
        return ResponseEntity.ok(reportService.getPlatformSummary());
    }

    @GetMapping("/reports/events")
    public ResponseEntity<List<EventReportDto>> getEventReports() {
        return ResponseEntity.ok(reportService.getEventReports());
    }

    @GetMapping(value = "/reports/export-csv", produces = "text/csv; charset=utf-8")
    public ResponseEntity<byte[]> exportEventsCsv() {
        String csv = reportService.generateEventsCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eventflow-revenue-report.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
            .body(bytes);
    }
}
