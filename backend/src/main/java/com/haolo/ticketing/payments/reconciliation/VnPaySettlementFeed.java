package com.haolo.ticketing.payments.reconciliation;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.haolo.ticketing.payments.reconciliation.ReconciliationModels.SettlementRecord;

@Component
public class VnPaySettlementFeed {

    private final JdbcTemplate jdbc;

    public VnPaySettlementFeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SettlementRecord> fetchSettlementFeed(LocalDate date, String simulationMode) {
        List<SettlementRecord> dbRecords = jdbc.query(
            "SELECT merchant_ref, provider_tx_ref, amount_vnd, created_at " +
            "FROM ticketing.payment_attempts " +
            "WHERE provider = 'VNPAY' AND status = 'SUCCESS' AND DATE(created_at) = ? " +
            "ORDER BY created_at ASC",
            (rs, rowNum) -> new SettlementRecord(
                rs.getString("provider_tx_ref") != null ? rs.getString("provider_tx_ref") : "VNP_" + System.currentTimeMillis() + "_" + rowNum,
                rs.getString("merchant_ref"),
                rs.getLong("amount_vnd"),
                "SUCCESS",
                rs.getTimestamp("created_at").toInstant()
            ),
            Date.valueOf(date)
        );

        if ("DISCREPANCY_DEMO".equalsIgnoreCase(simulationMode)) {
            List<SettlementRecord> mutated = new ArrayList<>();
            if (!dbRecords.isEmpty()) {
                // 1. Matched record
                mutated.add(dbRecords.getFirst());

                // 2. Amount mismatch (Gateway reports 50,000 VND less)
                if (dbRecords.size() > 1) {
                    SettlementRecord original = dbRecords.get(1);
                    mutated.add(new SettlementRecord(
                        original.providerTxRef(),
                        original.merchantRef(),
                        Math.max(10000L, original.amountVnd() - 50000L),
                        "SUCCESS",
                        original.settledAt()
                    ));
                } else {
                    mutated.add(new SettlementRecord(
                        "VNP_SIM_MISMATCH_1",
                        "EVF_MOCK_MISMATCH_01",
                        450000L,
                        "SUCCESS",
                        Instant.now()
                    ));
                }

                // 3. Skip index 2 (if present) to simulate MISSING_IN_GATEWAY
                for (int i = 3; i < dbRecords.size(); i++) {
                    mutated.add(dbRecords.get(i));
                }
            } else {
                mutated.add(new SettlementRecord("VNP_CLEAN_100", "EVF_DEMO_CLEAN_01", 500000L, "SUCCESS", Instant.now()));
                mutated.add(new SettlementRecord("VNP_MISMATCH_200", "EVF_DEMO_MISMATCH_02", 450000L, "SUCCESS", Instant.now()));
            }

            // 4. Injected MISSING_IN_DB (exists in Gateway settlement feed but not in DB)
            mutated.add(new SettlementRecord(
                "VNP_GHOST_ORPHAN_" + (System.currentTimeMillis() % 10000),
                "EVF_UNKNOWN_EXT_REF_999",
                300000L,
                "SUCCESS",
                Instant.now()
            ));

            return mutated;
        }

        // Clean mode: 100% matched
        if (dbRecords.isEmpty()) {
            return List.of(
                new SettlementRecord("VNP_AUTO_101", "EVF_DEMO_MERCHANT_01", 600000L, "SUCCESS", Instant.now()),
                new SettlementRecord("VNP_AUTO_102", "EVF_DEMO_MERCHANT_02", 1200000L, "SUCCESS", Instant.now())
            );
        }
        return dbRecords;
    }
}
