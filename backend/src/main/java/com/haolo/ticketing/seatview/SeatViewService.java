package com.haolo.ticketing.seatview;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.seatview.SeatViewModels.SeatViewDto;
import com.haolo.ticketing.shared.ApiException;

@Service
public class SeatViewService {

    private final JdbcTemplate jdbc;

    public SeatViewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SeatViewDto getSeatView(UUID eventId, UUID seatId) {
        if (eventId == null || seatId == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã sự kiện hoặc mã ghế");
        }

        String sql = """
            SELECT s.id, s.event_id, s.label, s.row_label, s.seat_number,
                   s.distance_meters, s.elevation_angle, s.view_quality_score, s.view_notes,
                   s.status, tt.price_vnd, tt.name AS ticket_type_name
            FROM ticketing.seats s
            JOIN ticketing.ticket_types tt ON s.ticket_type_id = tt.id
            WHERE s.event_id = ? AND s.id = ?
            """;

        List<SeatViewDto> results = jdbc.query(sql, (rs, rowNum) -> {
            String rowLabel = rs.getString("row_label");
            if (rowLabel == null || rowLabel.isBlank()) {
                String label = rs.getString("label");
                rowLabel = (label != null && !label.isEmpty()) ? label.substring(0, 1) : "A";
            }
            int seatNumber = rs.getInt("seat_number");
            if (rs.wasNull() || seatNumber <= 0) {
                seatNumber = 5;
            }

            double distance = rs.getDouble("distance_meters");
            if (rs.wasNull() || distance <= 0) {
                char rowChar = rowLabel.toUpperCase().charAt(0);
                int rowOffset = Math.max(0, rowChar - 'A');
                distance = 10.5 + (rowOffset * 2.0);
            }

            double elevation = rs.getDouble("elevation_angle");
            if (rs.wasNull() || elevation <= 0) {
                elevation = 12.0;
            }

            int score = rs.getInt("view_quality_score");
            if (rs.wasNull() || score <= 0) {
                score = Math.max(70, 98 - (int) (distance * 1.5));
            }

            String notes = rs.getString("view_notes");
            if (notes == null || notes.isBlank()) {
                notes = score >= 90 ? "Tầm nhìn chính diện sân khấu VIP, góc nhìn hoàn hảo"
                      : score >= 80 ? "Góc nhìn khán đài rộng, âm thanh trung thực"
                      : "Góc nhìn tổng thể khán đài";
            }

            String svg = generateStagePerspectiveSvg(rowLabel, seatNumber, distance, elevation, score);

            return new SeatViewDto(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("label"),
                rowLabel,
                seatNumber,
                distance,
                elevation,
                score,
                notes,
                svg,
                rs.getString("status"),
                rs.getLong("price_vnd"),
                rs.getString("ticket_type_name")
            );
        }, eventId, seatId);

        if (results.isEmpty()) {
            throw new ApiException(404, "SEAT_NOT_FOUND", "Không tìm thấy thông tin ghế yêu cầu");
        }

        return results.getFirst();
    }

    public String generateStagePerspectiveSvg(String rowLabel, int seatNumber, double distance, double elevation, int score) {
        // SVG dimensions 400x240
        int width = 400;
        int height = 240;

        // Stage coordinates
        int stageX = 100;
        int stageY = 25;
        int stageWidth = 200;
        int stageHeight = 45;

        // Viewer coordinates calculated from column (seatNumber) and row (distance)
        // Assume 10 seats per row; center is seat 5.5
        double colOffset = (seatNumber - 5.5) * 22.0;
        int viewerX = (int) (width / 2.0 + colOffset);
        // Distance controls Y position: 10m -> Y=180, 20m -> Y=220
        int viewerY = (int) Math.min(225, Math.max(160, 160 + (distance - 10.0) * 4.5));

        // Quality badge color
        String scoreColor = score >= 90 ? "#10b981" : (score >= 80 ? "#6366f1" : "#f59e0b");

        return String.format(
            java.util.Locale.ROOT,
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="100%%" height="100%%" class="seat-view-svg">
              <defs>
                <linearGradient id="stageGlow" x1="0%%" y1="0%%" x2="0%%" y2="100%%">
                  <stop offset="0%%" stop-color="#818cf8" stop-opacity="0.8"/>
                  <stop offset="100%%" stop-color="#4f46e5" stop-opacity="0.3"/>
                </linearGradient>
                <linearGradient id="beamGradLeft" x1="0%%" y1="0%%" x2="100%%" y2="100%%">
                  <stop offset="0%%" stop-color="#38bdf8" stop-opacity="0.35"/>
                  <stop offset="100%%" stop-color="#38bdf8" stop-opacity="0.0"/>
                </linearGradient>
                <linearGradient id="beamGradRight" x1="100%%" y1="0%%" x2="0%%" y2="100%%">
                  <stop offset="0%%" stop-color="#a855f7" stop-opacity="0.35"/>
                  <stop offset="100%%" stop-color="#a855f7" stop-opacity="0.0"/>
                </linearGradient>
              </defs>
              <rect width="%d" height="%d" fill="#0f172a" rx="12"/>
              <!-- Grid Floor Lines -->
              <line x1="40" y1="140" x2="360" y2="140" stroke="#1e293b" stroke-width="1" stroke-dasharray="4,4"/>
              <line x1="20" y1="180" x2="380" y2="180" stroke="#1e293b" stroke-width="1" stroke-dasharray="4,4"/>
              <line x1="10" y1="220" x2="390" y2="220" stroke="#1e293b" stroke-width="1" stroke-dasharray="4,4"/>
              
              <!-- Spotlight Beams -->
              <polygon points="120,%d 20,220 180,220" fill="url(#beamGradLeft)"/>
              <polygon points="280,%d 220,220 380,220" fill="url(#beamGradRight)"/>
              
              <!-- Stage Platform 3D Isometric View -->
              <polygon points="%d,%d %d,%d %d,%d %d,%d" fill="url(#stageGlow)" stroke="#a5b4fc" stroke-width="1.5"/>
              <polygon points="%d,%d %d,%d %d,%d %d,%d" fill="#312e81" stroke="#4338ca" stroke-width="1"/>
              <text x="%d" y="%d" fill="#ffffff" font-size="11" font-weight="bold" text-anchor="middle" letter-spacing="1">★ SÂN KHẤU CHÍNH ★</text>
              
              <!-- Sightlines (Field of View from Seat) -->
              <line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#f43f5e" stroke-width="1.5" stroke-dasharray="3,3" opacity="0.8"/>
              <line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#f43f5e" stroke-width="1.5" stroke-dasharray="3,3" opacity="0.8"/>
              <polygon points="%d,%d %d,%d %d,%d" fill="#f43f5e" fill-opacity="0.08"/>
              
              <!-- Viewer Position Dot & Halo -->
              <circle cx="%d" cy="%d" r="10" fill="#f43f5e" fill-opacity="0.25"/>
              <circle cx="%d" cy="%d" r="5" fill="#f43f5e" stroke="#ffffff" stroke-width="1.5"/>
              <text x="%d" y="%d" fill="#ffffff" font-size="10" font-weight="bold" text-anchor="middle">Ghế %s%d</text>
              
              <!-- Metric Badges in Top Corners -->
              <rect x="12" y="12" width="76" height="22" rx="4" fill="#1e293b" stroke="#334155" stroke-width="1"/>
              <text x="50" y="27" fill="#94a3b8" font-size="10" text-anchor="middle">Cự ly: %.1fm</text>
              
              <rect x="%d" y="12" width="82" height="22" rx="4" fill="#1e293b" stroke="%s" stroke-width="1"/>
              <text x="%d" y="27" fill="%s" font-size="10" font-weight="bold" text-anchor="middle">Điểm: %d/100</text>
            </svg>
            """,
            width, height,
            width, height,
            stageY + stageHeight,
            stageY + stageHeight,
            stageX, stageY + 10,  stageX + stageWidth, stageY + 10,  stageX + stageWidth - 15, stageY + stageHeight,  stageX + 15, stageY + stageHeight,
            stageX + 15, stageY + stageHeight,  stageX + stageWidth - 15, stageY + stageHeight,  stageX + stageWidth - 15, stageY + stageHeight + 10,  stageX + 15, stageY + stageHeight + 10,
            width / 2, stageY + 28,
            viewerX, viewerY, stageX + 15, stageY + stageHeight,
            viewerX, viewerY, stageX + stageWidth - 15, stageY + stageHeight,
            viewerX, viewerY, stageX + 15, stageY + stageHeight, stageX + stageWidth - 15, stageY + stageHeight,
            viewerX, viewerY,
            viewerX, viewerY,
            viewerX, viewerY + 18, rowLabel, seatNumber,
            distance,
            width - 94, scoreColor,
            width - 53, scoreColor, score
        );
    }
}
