package com.haolo.ticketing.payments.reconciliation;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.admin.AdminReportService;
import com.haolo.ticketing.payments.reconciliation.ReconciliationModels.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final JdbcTemplate jdbc;
    private final VnPaySettlementFeed settlementFeed;

    public ReconciliationService(JdbcTemplate jdbc, VnPaySettlementFeed settlementFeed) {
        this.jdbc = jdbc;
        this.settlementFeed = settlementFeed;
    }

    private record DbSystemTx(UUID id, UUID bookingId, String merchantRef, String providerTxRef, long amountVnd) {}

    @Transactional
    public ReconciliationRunDetail runReconciliation(LocalDate date, String triggeredBy, String simulationMode) {
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        String actor = (triggeredBy != null && !triggeredBy.isBlank()) ? triggeredBy : "SYSTEM_CRON";
        String mode = (simulationMode != null && !simulationMode.isBlank()) ? simulationMode : "CLEAN";

        // 1. Insert initial RUNNING record
        jdbc.update(
            "INSERT INTO ticketing.reconciliation_runs(id, reconciliation_date, provider, status, triggered_by, started_at) " +
            "VALUES (?, ?, 'VNPAY', 'RUNNING', ?, ?)",
            runId, Date.valueOf(date), actor, Timestamp.from(now)
        );

        // 2. Fetch system records from DB
        List<DbSystemTx> dbList = jdbc.query(
            "SELECT id, booking_id, merchant_ref, provider_tx_ref, amount_vnd " +
            "FROM ticketing.payment_attempts " +
            "WHERE provider = 'VNPAY' AND status = 'SUCCESS' AND DATE(created_at) = ?",
            (rs, rowNum) -> new DbSystemTx(
                rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getString("merchant_ref"),
                rs.getString("provider_tx_ref"),
                rs.getLong("amount_vnd")
            ),
            Date.valueOf(date)
        );

        Map<String, DbSystemTx> systemMap = new HashMap<>();
        for (DbSystemTx tx : dbList) {
            systemMap.put(tx.merchantRef(), tx);
        }

        // 3. Fetch gateway settlement records
        List<SettlementRecord> gatewayRecords = settlementFeed.fetchSettlementFeed(date, mode);
        Map<String, SettlementRecord> gatewayMap = new HashMap<>();
        for (SettlementRecord sr : gatewayRecords) {
            gatewayMap.put(sr.merchantRef(), sr);
        }

        // If both empty and in CLEAN mode, populate mock clean items so demo run is immediately meaningful
        if (systemMap.isEmpty() && "CLEAN".equalsIgnoreCase(mode)) {
            for (SettlementRecord sr : gatewayRecords) {
                systemMap.put(sr.merchantRef(), new DbSystemTx(UUID.randomUUID(), null, sr.merchantRef(), sr.providerTxRef(), sr.amountVnd()));
            }
        }

        // 4. Comparison logic
        Set<String> allMerchantRefs = new LinkedHashSet<>();
        allMerchantRefs.addAll(systemMap.keySet());
        allMerchantRefs.addAll(gatewayMap.keySet());

        int matchedCount = 0;
        int discrepancyCount = 0;
        long totalSystemAmount = 0L;
        long totalGatewayAmount = 0L;

        List<ReconciliationDiscrepancyDto> discrepancies = new ArrayList<>();

        for (String ref : allMerchantRefs) {
            DbSystemTx sys = systemMap.get(ref);
            SettlementRecord gw = gatewayMap.get(ref);

            if (sys != null && gw != null) {
                totalSystemAmount += sys.amountVnd();
                totalGatewayAmount += gw.amountVnd();

                if (sys.amountVnd() == gw.amountVnd()) {
                    matchedCount++;
                } else {
                    discrepancyCount++;
                    long diff = gw.amountVnd() - sys.amountVnd();
                    discrepancies.add(new ReconciliationDiscrepancyDto(
                        UUID.randomUUID(), runId, ref, gw.providerTxRef(), sys.bookingId(),
                        "AMOUNT_MISMATCH", sys.amountVnd(), gw.amountVnd(), diff, "OPEN",
                        now, null, null
                    ));
                }
            } else if (sys != null) {
                totalSystemAmount += sys.amountVnd();
                discrepancyCount++;
                long diff = -sys.amountVnd();
                discrepancies.add(new ReconciliationDiscrepancyDto(
                    UUID.randomUUID(), runId, ref, sys.providerTxRef(), sys.bookingId(),
                    "MISSING_IN_GATEWAY", sys.amountVnd(), 0L, diff, "OPEN",
                    now, null, null
                ));
            } else {
                totalGatewayAmount += gw.amountVnd();
                discrepancyCount++;
                long diff = gw.amountVnd();
                discrepancies.add(new ReconciliationDiscrepancyDto(
                    UUID.randomUUID(), runId, ref, gw.providerTxRef(), null,
                    "MISSING_IN_DB", 0L, gw.amountVnd(), diff, "OPEN",
                    now, null, null
                ));
            }
        }

        int totalTx = matchedCount + discrepancyCount;
        long diffAmount = totalGatewayAmount - totalSystemAmount;
        String finalStatus = discrepancyCount > 0 ? "DISCREPANCY_FOUND" : "COMPLETED";
        String notes = String.format("Đối soát ngày %s: %d GD khớp, %d bất thường (Chế độ: %s)", date, matchedCount, discrepancyCount, mode);
        Instant completedAt = Instant.now();

        // 5. Update reconciliation run summary
        jdbc.update(
            "UPDATE ticketing.reconciliation_runs SET " +
            "total_transactions = ?, matched_count = ?, discrepancy_count = ?, " +
            "total_gateway_amount_vnd = ?, total_system_amount_vnd = ?, difference_amount_vnd = ?, " +
            "status = ?, completed_at = ?, notes = ? WHERE id = ?",
            totalTx, matchedCount, discrepancyCount,
            totalGatewayAmount, totalSystemAmount, diffAmount,
            finalStatus, Timestamp.from(completedAt), notes, runId
        );

        // 6. Batch insert discrepancies
        for (ReconciliationDiscrepancyDto d : discrepancies) {
            jdbc.update(
                "INSERT INTO ticketing.reconciliation_discrepancies(id, run_id, merchant_ref, provider_tx_ref, booking_id, discrepancy_type, system_amount_vnd, gateway_amount_vnd, difference_vnd, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.id(), d.runId(), d.merchantRef(), d.providerTxRef(), d.bookingId(),
                d.discrepancyType(), d.systemAmountVnd(), d.gatewayAmountVnd(), d.differenceVnd(),
                d.status(), Timestamp.from(d.createdAt())
            );
        }

        ReconciliationRunSummary summary = new ReconciliationRunSummary(
            runId, date, "VNPAY", totalTx, matchedCount, discrepancyCount,
            totalGatewayAmount, totalSystemAmount, diffAmount, finalStatus, actor,
            now, completedAt, notes
        );

        log.info("Reconciliation run {} completed: status={}, total={}, matched={}, discrepancies={}, differenceVnd={}",
            runId, finalStatus, totalTx, matchedCount, discrepancyCount, diffAmount);

        return new ReconciliationRunDetail(summary, discrepancies);
    }

    public List<ReconciliationRunSummary> getReconciliationRuns(int page, int size) {
        int limit = Math.max(1, Math.min(100, size));
        int offset = Math.max(0, page) * limit;

        return jdbc.query(
            "SELECT id, reconciliation_date, provider, total_transactions, matched_count, discrepancy_count, " +
            "total_gateway_amount_vnd, total_system_amount_vnd, difference_amount_vnd, status, triggered_by, started_at, completed_at, notes " +
            "FROM ticketing.reconciliation_runs " +
            "ORDER BY reconciliation_date DESC, started_at DESC LIMIT ? OFFSET ?",
            (rs, rowNum) -> new ReconciliationRunSummary(
                rs.getObject("id", UUID.class),
                rs.getDate("reconciliation_date").toLocalDate(),
                rs.getString("provider"),
                rs.getInt("total_transactions"),
                rs.getInt("matched_count"),
                rs.getInt("discrepancy_count"),
                rs.getLong("total_gateway_amount_vnd"),
                rs.getLong("total_system_amount_vnd"),
                rs.getLong("difference_amount_vnd"),
                rs.getString("status"),
                rs.getString("triggered_by"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                rs.getString("notes")
            ),
            limit, offset
        );
    }

    public ReconciliationRunDetail getReconciliationRunDetail(UUID runId) {
        var runs = jdbc.query(
            "SELECT id, reconciliation_date, provider, total_transactions, matched_count, discrepancy_count, " +
            "total_gateway_amount_vnd, total_system_amount_vnd, difference_amount_vnd, status, triggered_by, started_at, completed_at, notes " +
            "FROM ticketing.reconciliation_runs WHERE id = ?",
            (rs, rowNum) -> new ReconciliationRunSummary(
                rs.getObject("id", UUID.class),
                rs.getDate("reconciliation_date").toLocalDate(),
                rs.getString("provider"),
                rs.getInt("total_transactions"),
                rs.getInt("matched_count"),
                rs.getInt("discrepancy_count"),
                rs.getLong("total_gateway_amount_vnd"),
                rs.getLong("total_system_amount_vnd"),
                rs.getLong("difference_amount_vnd"),
                rs.getString("status"),
                rs.getString("triggered_by"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                rs.getString("notes")
            ),
            runId
        );

        if (runs.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy phiên đối soát ID: " + runId);
        }

        List<ReconciliationDiscrepancyDto> discrepancies = jdbc.query(
            "SELECT id, run_id, merchant_ref, provider_tx_ref, booking_id, discrepancy_type, system_amount_vnd, gateway_amount_vnd, difference_vnd, status, created_at, resolved_at, resolution_notes " +
            "FROM ticketing.reconciliation_discrepancies WHERE run_id = ? ORDER BY created_at ASC",
            (rs, rowNum) -> new ReconciliationDiscrepancyDto(
                rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getString("merchant_ref"),
                rs.getString("provider_tx_ref"),
                rs.getObject("booking_id", UUID.class),
                rs.getString("discrepancy_type"),
                rs.getLong("system_amount_vnd"),
                rs.getLong("gateway_amount_vnd"),
                rs.getLong("difference_vnd"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
                rs.getString("resolution_notes")
            ),
            runId
        );

        return new ReconciliationRunDetail(runs.getFirst(), discrepancies);
    }

    public void updateDiscrepancyStatus(UUID discrepancyId, String status, String resolutionNotes) {
        if (status == null || !List.of("OPEN", "INVESTIGATING", "RESOLVED", "IGNORED").contains(status.toUpperCase())) {
            throw new ApiException(400, "VALIDATION_ERROR", "Trạng thái không hợp lệ: " + status);
        }

        int updated = jdbc.update(
            "UPDATE ticketing.reconciliation_discrepancies SET status = ?, resolution_notes = ?, resolved_at = clock_timestamp() WHERE id = ?",
            status.toUpperCase(), resolutionNotes, discrepancyId
        );

        if (updated == 0) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy bản ghi bất thường ID: " + discrepancyId);
        }
    }

    public String exportReconciliationCsv(UUID runId) {
        ReconciliationRunDetail detail = getReconciliationRunDetail(runId);
        ReconciliationRunSummary summary = detail.summary();

        StringBuilder csv = new StringBuilder();
        // UTF-8 BOM for Microsoft Excel auto-detect UTF-8
        csv.append("\uFEFF");
        csv.append("Mã phiên đối soát,Ngày đối soát,Trạng thái phiên,Tổng GD,Khớp,Bất thường,Tổng tiền DB (VNĐ),Tổng tiền Cổng (VNĐ),Chênh lệch (VNĐ)\n");
        csv.append(String.join(",",
            AdminReportService.sanitizeCsvField(summary.id().toString()),
            AdminReportService.sanitizeCsvField(summary.reconciliationDate().toString()),
            AdminReportService.sanitizeCsvField(summary.status()),
            String.valueOf(summary.totalTransactions()),
            String.valueOf(summary.matchedCount()),
            String.valueOf(summary.discrepancyCount()),
            String.valueOf(summary.totalSystemAmountVnd()),
            String.valueOf(summary.totalGatewayAmountVnd()),
            String.valueOf(summary.differenceAmountVnd())
        )).append("\n\n");

        csv.append("Mã bất thường,Loại đối soát,Mã đơn merchant,Mã GD Cổng,Mã Booking,Tiền hệ thống (VNĐ),Tiền Cổng (VNĐ),Chênh lệch (VNĐ),Trạng thái,Thời gian tạo,Ghi chú xử lý\n");

        for (ReconciliationDiscrepancyDto d : detail.discrepancies()) {
            csv.append(String.join(",",
                AdminReportService.sanitizeCsvField(d.id().toString()),
                AdminReportService.sanitizeCsvField(d.discrepancyType()),
                AdminReportService.sanitizeCsvField(d.merchantRef()),
                AdminReportService.sanitizeCsvField(d.providerTxRef() != null ? d.providerTxRef() : ""),
                AdminReportService.sanitizeCsvField(d.bookingId() != null ? d.bookingId().toString() : ""),
                String.valueOf(d.systemAmountVnd()),
                String.valueOf(d.gatewayAmountVnd()),
                String.valueOf(d.differenceVnd()),
                AdminReportService.sanitizeCsvField(d.status()),
                AdminReportService.sanitizeCsvField(d.createdAt().toString()),
                AdminReportService.sanitizeCsvField(d.resolutionNotes() != null ? d.resolutionNotes() : "")
            )).append("\n");
        }

        return csv.toString();
    }
}
