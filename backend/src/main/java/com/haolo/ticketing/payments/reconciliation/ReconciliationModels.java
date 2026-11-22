package com.haolo.ticketing.payments.reconciliation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ReconciliationModels {

    public record SettlementRecord(
        String providerTxRef,
        String merchantRef,
        long amountVnd,
        String status,
        Instant settledAt
    ) {}

    public record ReconciliationRunSummary(
        UUID id,
        LocalDate reconciliationDate,
        String provider,
        int totalTransactions,
        int matchedCount,
        int discrepancyCount,
        long totalGatewayAmountVnd,
        long totalSystemAmountVnd,
        long differenceAmountVnd,
        String status,
        String triggeredBy,
        Instant startedAt,
        Instant completedAt,
        String notes
    ) {}

    public record ReconciliationDiscrepancyDto(
        UUID id,
        UUID runId,
        String merchantRef,
        String providerTxRef,
        UUID bookingId,
        String discrepancyType,
        long systemAmountVnd,
        long gatewayAmountVnd,
        long differenceVnd,
        String status,
        Instant createdAt,
        Instant resolvedAt,
        String resolutionNotes
    ) {}

    public record ReconciliationRunDetail(
        ReconciliationRunSummary summary,
        List<ReconciliationDiscrepancyDto> discrepancies
    ) {}

    public record RunReconciliationRequest(
        String date,
        String simulationMode
    ) {}

    public record UpdateDiscrepancyStatusRequest(
        String status,
        String resolutionNotes
    ) {}
}
