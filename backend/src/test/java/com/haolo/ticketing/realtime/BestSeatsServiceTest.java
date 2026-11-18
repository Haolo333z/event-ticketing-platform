package com.haolo.ticketing.realtime;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.haolo.ticketing.realtime.BestSeatsService.*;
import com.haolo.ticketing.shared.ApiException;
import static org.junit.jupiter.api.Assertions.*;

class BestSeatsServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TYPE_ID = UUID.randomUUID();

    private static List<SeatRow> row(String rowLabel, int count, Set<Integer> taken) {
        List<SeatRow> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new SeatRow(UUID.randomUUID(), rowLabel + i, taken.contains(i) ? "HELD" : "AVAILABLE", rowLabel, i));
        }
        return list;
    }

    @Test
    void picksCenterContiguousBlockInFrontRow() {
        List<SeatRow> seats = new ArrayList<>();
        seats.addAll(row("A", 10, Set.of()));
        seats.addAll(row("B", 10, Set.of()));

        BestSeatsResult res = BestSeatsService.selectBestBlock(EVENT_ID, TYPE_ID, 2, seats);
        assertTrue(res.contiguous());
        assertEquals("A", res.rowLabel());
        assertEquals(List.of("A5", "A6"), res.seats().stream().map(SeatPick::label).toList());
        assertEquals(0.0, res.centerDistance());
    }

    @Test
    void skipsTakenCenterSeatsAndFindsNextBestContiguousBlock() {
        List<SeatRow> seats = new ArrayList<>();
        // A5, A6 taken -> remaining blocks of 3 in A are [A1,A2,A3], [A2,A3,A4], [A7,A8,A9], [A8,A9,A10]
        seats.addAll(row("A", 10, Set.of(5, 6)));

        BestSeatsResult res = BestSeatsService.selectBestBlock(EVENT_ID, TYPE_ID, 3, seats);
        assertTrue(res.contiguous());
        assertEquals("A", res.rowLabel());
        assertEquals(List.of("A2", "A3", "A4"), res.seats().stream().map(SeatPick::label).toList());
    }

    @Test
    void movesToRowBWhenRowAHasNoContiguousBlock() {
        List<SeatRow> seats = new ArrayList<>();
        // Row A fragmented: only A1, A3, A5 available
        seats.addAll(row("A", 6, Set.of(2, 4, 6)));
        seats.addAll(row("B", 6, Set.of()));

        BestSeatsResult res = BestSeatsService.selectBestBlock(EVENT_ID, TYPE_ID, 2, seats);
        assertTrue(res.contiguous());
        assertEquals("B", res.rowLabel());
        assertEquals(List.of("B3", "B4"), res.seats().stream().map(SeatPick::label).toList());
    }

    @Test
    void fallsBackToNonContiguousWhenNoContiguousBlockExists() {
        List<SeatRow> seats = new ArrayList<>();
        seats.addAll(row("A", 4, Set.of(2, 4))); // A1, A3 available

        BestSeatsResult res = BestSeatsService.selectBestBlock(EVENT_ID, TYPE_ID, 2, seats);
        assertFalse(res.contiguous());
        assertEquals(List.of("A1", "A3"), res.seats().stream().map(SeatPick::label).toList());
    }

    @Test
    void throws409WhenNotEnoughSeatsAvailable() {
        List<SeatRow> seats = row("A", 3, Set.of(1, 2)); // only A3 available
        ApiException ex = assertThrows(ApiException.class,
            () -> BestSeatsService.selectBestBlock(EVENT_ID, TYPE_ID, 2, seats));
        assertEquals(409, ex.status);
        assertEquals("INVENTORY_UNAVAILABLE", ex.code);
    }
}
