package com.haolo.ticketing.realtime;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.shared.ApiException;

@Service
public class BestSeatsService {
    private static final Pattern LABEL_PATTERN = Pattern.compile("^(.*?)(\\d+)$");

    public record SeatRow(UUID id, String label, String status, String rowLabel, Integer seatNumber) {}
    public record SeatPick(UUID id, String label, String rowLabel, int seatNumber) {}
    public record BestSeatsResult(
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        boolean contiguous,
        String rowLabel,
        double centerDistance,
        List<SeatPick> seats,
        String explanation
    ) {}

    private final JdbcTemplate jdbc;

    public BestSeatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BestSeatsResult findBestContiguousSeats(UUID eventId, UUID ticketTypeId, int quantity) {
        if (quantity < 1 || quantity > 6) {
            throw new ApiException(400, "VALIDATION_ERROR", "Số lượng ghế gợi ý phải từ 1 đến 6");
        }
        List<SeatRow> allSeats = jdbc.query(
            "SELECT id, label, status, row_label, seat_number FROM ticketing.seats WHERE event_id = ? AND ticket_type_id = ? ORDER BY id",
            (rs, n) -> new SeatRow(
                rs.getObject("id", UUID.class),
                rs.getString("label"),
                rs.getString("status"),
                rs.getString("row_label"),
                (Integer) rs.getObject("seat_number")
            ),
            eventId, ticketTypeId
        );
        if (allSeats.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy sơ đồ ghế cho hạng vé này");
        }
        return selectBestBlock(eventId, ticketTypeId, quantity, allSeats);
    }

    /** Pure algorithmic core — easily unit-testable without database. */
    public static BestSeatsResult selectBestBlock(UUID eventId, UUID ticketTypeId, int quantity, List<SeatRow> allSeats) {
        Map<String, List<NormalizedSeat>> byRow = new TreeMap<>(BestSeatsService::compareRowLabels);
        for (SeatRow raw : allSeats) {
            NormalizedSeat norm = normalize(raw);
            byRow.computeIfAbsent(norm.rowLabel, k -> new ArrayList<>()).add(norm);
        }

        List<NormalizedSeat> bestContiguous = null;
        String bestRow = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double bestCenterDist = Double.POSITIVE_INFINITY;

        int rowIndex = 0;
        for (var entry : byRow.entrySet()) {
            String row = entry.getKey();
            List<NormalizedSeat> rowSeats = entry.getValue();
            rowSeats.sort(Comparator.comparingInt(s -> s.seatNumber));

            int minNum = rowSeats.getFirst().seatNumber;
            int maxNum = rowSeats.getLast().seatNumber;
            double rowCenter = (minNum + maxNum) / 2.0;

            for (int i = 0; i <= rowSeats.size() - quantity; i++) {
                boolean valid = true;
                double sumNum = 0;
                for (int j = 0; j < quantity; j++) {
                    NormalizedSeat current = rowSeats.get(i + j);
                    if (!"AVAILABLE".equals(current.status)) {
                        valid = false;
                        break;
                    }
                    if (j > 0 && current.seatNumber != rowSeats.get(i + j - 1).seatNumber + 1) {
                        valid = false;
                        break;
                    }
                    sumNum += current.seatNumber;
                }
                if (valid) {
                    double blockCenter = sumNum / quantity;
                    double centerDistance = Math.abs(blockCenter - rowCenter);
                    // Front rows preferred slightly more than back rows, with center view as primary tie-breaker
                    double score = (rowIndex * 1.5) + centerDistance;
                    if (score < bestScore) {
                        bestScore = score;
                        bestCenterDist = Math.round(centerDistance * 100.0) / 100.0;
                        bestRow = row;
                        bestContiguous = new ArrayList<>(rowSeats.subList(i, i + quantity));
                    }
                }
            }
            rowIndex++;
        }

        if (bestContiguous != null) {
            List<SeatPick> picks = bestContiguous.stream()
                .map(s -> new SeatPick(s.id, s.label, s.rowLabel, s.seatNumber))
                .toList();
            String seatNames = String.join(", ", picks.stream().map(SeatPick::label).toList());
            return new BestSeatsResult(
                eventId, ticketTypeId, quantity, true, bestRow, bestCenterDist, picks,
                "Đã chọn cụm " + quantity + " ghế liền kề tốt nhất tại hàng " + bestRow + " (" + seatNames + ") có góc nhìn trung tâm sân khấu."
            );
        }

        // Fallback: pick nearest available non-contiguous seats if enough exist
        List<NormalizedSeat> available = byRow.values().stream()
            .flatMap(List::stream)
            .filter(s -> "AVAILABLE".equals(s.status))
            .toList();
        if (available.size() < quantity) {
            throw new ApiException(409, "INVENTORY_UNAVAILABLE",
                "Hiện chỉ còn " + available.size() + " ghế trống, không đủ " + quantity + " ghế yêu cầu");
        }
        List<SeatPick> fallbackPicks = available.subList(0, quantity).stream()
            .map(s -> new SeatPick(s.id, s.label, s.rowLabel, s.seatNumber))
            .toList();
        String seatNames = String.join(", ", fallbackPicks.stream().map(SeatPick::label).toList());
        return new BestSeatsResult(
            eventId, ticketTypeId, quantity, false, fallbackPicks.getFirst().rowLabel(), 0.0, fallbackPicks,
            "Không còn cụm " + quantity + " ghế liền kề cùng hàng; hệ thống gợi ý các ghế trống gần sân khấu nhất: " + seatNames
        );
    }

    private record NormalizedSeat(UUID id, String label, String status, String rowLabel, int seatNumber) {}

    private static NormalizedSeat normalize(SeatRow raw) {
        String row = raw.rowLabel();
        Integer num = raw.seatNumber();
        if (row == null || row.isBlank() || num == null) {
            Matcher m = LABEL_PATTERN.matcher(raw.label() != null ? raw.label().trim() : "");
            if (m.matches()) {
                row = (row == null || row.isBlank()) ? (m.group(1).isBlank() ? "ROW" : m.group(1)) : row;
                num = num != null ? num : Integer.parseInt(m.group(2));
            } else {
                row = (row == null || row.isBlank()) ? "ROW" : row;
                num = num != null ? num : 0;
            }
        }
        return new NormalizedSeat(raw.id(), raw.label(), raw.status(), row, num);
    }

    private static int compareRowLabels(String a, String b) {
        if (a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return a.compareToIgnoreCase(b);
    }
}
