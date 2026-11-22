package com.haolo.ticketing.payments.reconciliation;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.payments.reconciliation.ReconciliationModels.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api/admin/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    private void assertAdmin(DemoPrincipal principal) {
        if (principal == null || !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Chỉ quản trị viên mới có quyền truy cập module đối soát tài chính");
        }
    }

    @PostMapping("/run")
    public ResponseEntity<ReconciliationRunDetail> triggerReconciliation(
            @AuthenticationPrincipal DemoPrincipal principal,
            @RequestBody(required = false) RunReconciliationRequest req) {
        assertAdmin(principal);

        LocalDate date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String simulationMode = "CLEAN";

        if (req != null) {
            if (req.date() != null && !req.date().isBlank()) {
                try {
                    date = LocalDate.parse(req.date());
                } catch (Exception e) {
                    throw new ApiException(400, "VALIDATION_ERROR", "Định dạng ngày không hợp lệ (YYYY-MM-DD): " + req.date());
                }
            }
            if (req.simulationMode() != null && !req.simulationMode().isBlank()) {
                simulationMode = req.simulationMode().toUpperCase();
            }
        }

        String triggeredBy = "ADMIN:" + (principal != null ? principal.accountId() : "System");
        ReconciliationRunDetail result = reconciliationService.runReconciliation(date, triggeredBy, simulationMode);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/runs")
    public ResponseEntity<List<ReconciliationRunSummary>> getRuns(
            @AuthenticationPrincipal DemoPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        assertAdmin(principal);
        return ResponseEntity.ok(reconciliationService.getReconciliationRuns(page, size));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<ReconciliationRunDetail> getRunDetail(
            @AuthenticationPrincipal DemoPrincipal principal,
            @PathVariable UUID id) {
        assertAdmin(principal);
        return ResponseEntity.ok(reconciliationService.getReconciliationRunDetail(id));
    }

    @GetMapping("/runs/{id}/export-csv")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal DemoPrincipal principal,
            @PathVariable UUID id) {
        assertAdmin(principal);
        String csvContent = reconciliationService.exportReconciliationCsv(id);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eventflow-reconciliation-" + id + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }

    @PutMapping("/discrepancies/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @AuthenticationPrincipal DemoPrincipal principal,
            @PathVariable UUID id,
            @RequestBody UpdateDiscrepancyStatusRequest req) {
        assertAdmin(principal);
        if (req == null || req.status() == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thiếu trạng thái cập nhật");
        }
        reconciliationService.updateDiscrepancyStatus(id, req.status(), req.resolutionNotes());
        return ResponseEntity.ok(Map.of("success", true, "discrepancyId", id.toString(), "status", req.status()));
    }
}
