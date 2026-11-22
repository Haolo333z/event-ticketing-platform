package com.haolo.ticketing.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.payments.reconciliation.*;
import com.haolo.ticketing.payments.reconciliation.ReconciliationModels.*;
import com.haolo.ticketing.shared.ApiException;

class ReconciliationServiceTest {

    private JdbcTemplate jdbc;
    private VnPaySettlementFeed feed;
    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        feed = mock(VnPaySettlementFeed.class);
        service = new ReconciliationService(jdbc, feed);
    }

    @Test
    void runReconciliationCleanModeAllMatched() {
        LocalDate date = LocalDate.of(2026, 11, 21);

        // Mock feed returns 2 clean matched records
        when(feed.fetchSettlementFeed(eq(date), anyString())).thenReturn(List.of(
            new SettlementRecord("VNP_101", "EVF_01", 500000L, "SUCCESS", Instant.now()),
            new SettlementRecord("VNP_102", "EVF_02", 1000000L, "SUCCESS", Instant.now())
        ));

        // Mock DB returns same 2 records
        doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            var rs1 = mock(java.sql.ResultSet.class);
            when(rs1.getString("merchant_ref")).thenReturn("EVF_01");
            when(rs1.getString("provider_tx_ref")).thenReturn("VNP_101");
            when(rs1.getLong("amount_vnd")).thenReturn(500000L);

            var rs2 = mock(java.sql.ResultSet.class);
            when(rs2.getString("merchant_ref")).thenReturn("EVF_02");
            when(rs2.getString("provider_tx_ref")).thenReturn("VNP_102");
            when(rs2.getLong("amount_vnd")).thenReturn(1000000L);

            return List.of(mapper.mapRow(rs1, 0), mapper.mapRow(rs2, 1));
        }).when(jdbc).query(contains("FROM ticketing.payment_attempts"), any(RowMapper.class), any(Date.class));

        var result = service.runReconciliation(date, "ADMIN:Test", "CLEAN");

        assertNotNull(result);
        assertEquals("COMPLETED", result.summary().status());
        assertEquals(2, result.summary().matchedCount());
        assertEquals(0, result.summary().discrepancyCount());
        assertTrue(result.discrepancies().isEmpty());

        // Verify update reconciliation_runs called with COMPLETED
        verify(jdbc, atLeastOnce()).update(
            contains("UPDATE ticketing.reconciliation_runs"),
            eq(2), eq(2), eq(0), anyLong(), anyLong(), anyLong(), eq("COMPLETED"), any(), anyString(), any(UUID.class)
        );
    }

    @Test
    void runReconciliationDiscrepancyDemoDetectsAllThreeTypes() {
        LocalDate date = LocalDate.of(2026, 11, 21);

        // Gateway feed has:
        // 1. EVF_MATCHED (exact 500k)
        // 2. EVF_MISMATCH (450k gateway vs 500k system -> AMOUNT_MISMATCH)
        // 3. EVF_GHOST (300k in gateway only -> MISSING_IN_DB)
        when(feed.fetchSettlementFeed(eq(date), eq("DISCREPANCY_DEMO"))).thenReturn(List.of(
            new SettlementRecord("VNP_1", "EVF_MATCHED", 500000L, "SUCCESS", Instant.now()),
            new SettlementRecord("VNP_2", "EVF_MISMATCH", 450000L, "SUCCESS", Instant.now()),
            new SettlementRecord("VNP_3", "EVF_GHOST", 300000L, "SUCCESS", Instant.now())
        ));

        // System DB has:
        // 1. EVF_MATCHED (500k)
        // 2. EVF_MISMATCH (500k)
        // 3. EVF_MISSING_GW (200k in system only -> MISSING_IN_GATEWAY)
        doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            var rs1 = mock(java.sql.ResultSet.class);
            when(rs1.getString("merchant_ref")).thenReturn("EVF_MATCHED");
            when(rs1.getString("provider_tx_ref")).thenReturn("VNP_1");
            when(rs1.getLong("amount_vnd")).thenReturn(500000L);

            var rs2 = mock(java.sql.ResultSet.class);
            when(rs2.getString("merchant_ref")).thenReturn("EVF_MISMATCH");
            when(rs2.getString("provider_tx_ref")).thenReturn("VNP_2");
            when(rs2.getLong("amount_vnd")).thenReturn(500000L);

            var rs3 = mock(java.sql.ResultSet.class);
            when(rs3.getString("merchant_ref")).thenReturn("EVF_MISSING_GW");
            when(rs3.getString("provider_tx_ref")).thenReturn("VNP_4");
            when(rs3.getLong("amount_vnd")).thenReturn(200000L);

            return List.of(
                mapper.mapRow(rs1, 0),
                mapper.mapRow(rs2, 1),
                mapper.mapRow(rs3, 2)
            );
        }).when(jdbc).query(contains("FROM ticketing.payment_attempts"), any(RowMapper.class), any(Date.class));

        var result = service.runReconciliation(date, "ADMIN:Test", "DISCREPANCY_DEMO");

        assertNotNull(result);
        assertEquals("DISCREPANCY_FOUND", result.summary().status());
        assertEquals(1, result.summary().matchedCount());
        assertEquals(3, result.summary().discrepancyCount());
        assertEquals(3, result.discrepancies().size());

        Set<String> types = new HashSet<>();
        for (var d : result.discrepancies()) {
            types.add(d.discrepancyType());
        }
        assertTrue(types.contains("AMOUNT_MISMATCH"));
        assertTrue(types.contains("MISSING_IN_GATEWAY"));
        assertTrue(types.contains("MISSING_IN_DB"));

        // Verify batch insert discrepancies called 3 times
        verify(jdbc, times(3)).update(
            contains("INSERT INTO ticketing.reconciliation_discrepancies"),
            any(UUID.class), any(UUID.class), anyString(), any(), any(), anyString(), anyLong(), anyLong(), anyLong(), anyString(), any()
        );
    }

    @Test
    void exportReconciliationCsvSanitizesFormulas() {
        UUID runId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 21);

        ReconciliationRunSummary summary = new ReconciliationRunSummary(
            runId, date, "VNPAY", 1, 0, 1, 450000L, 500000L, -50000L,
            "DISCREPANCY_FOUND", "ADMIN:Test", Instant.now(), Instant.now(), "Notes"
        );

        // Malicious formula injection attempt in merchant_ref
        ReconciliationDiscrepancyDto maliciousDto = new ReconciliationDiscrepancyDto(
            UUID.randomUUID(), runId, "=cmd|' /C calc'!A0", "+1234567890", null,
            "AMOUNT_MISMATCH", 500000L, 450000L, -50000L, "OPEN",
            Instant.now(), null, "@malicious_note"
        );

        when(jdbc.query(contains("FROM ticketing.reconciliation_runs WHERE id = ?"), any(RowMapper.class), eq(runId)))
            .thenReturn(List.of(summary));
        when(jdbc.query(contains("FROM ticketing.reconciliation_discrepancies WHERE run_id = ?"), any(RowMapper.class), eq(runId)))
            .thenReturn(List.of(maliciousDto));

        String csv = service.exportReconciliationCsv(runId);

        assertNotNull(csv);
        assertTrue(csv.startsWith("\uFEFF"), "Must start with UTF-8 BOM");
        assertTrue(csv.contains("'\u003dcmd|"), "Formula '=' must be sanitized with leading quote: " + csv);
        assertTrue(csv.contains("'+1234567890"), "Formula '+' must be sanitized with leading quote");
        assertTrue(csv.contains("'@malicious_note"), "Formula '@' must be sanitized with leading quote");
    }

    @Test
    void updateDiscrepancyStatusValidatesStatus() {
        UUID id = UUID.randomUUID();
        when(jdbc.update(contains("UPDATE ticketing.reconciliation_discrepancies"), eq("RESOLVED"), eq("Fixed"), eq(id)))
            .thenReturn(1);

        assertDoesNotThrow(() -> service.updateDiscrepancyStatus(id, "RESOLVED", "Fixed"));

        assertThrows(ApiException.class, () -> service.updateDiscrepancyStatus(id, "INVALID_STATE", "None"));
    }
}
