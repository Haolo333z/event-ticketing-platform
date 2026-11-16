package com.haolo.ticketing.admin;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.admin.AdminModels.*;

@Service
public class AdminReportService {

    private final JdbcTemplate jdbc;

    public AdminReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String sanitizeCsvField(String value) {
        if (value == null) return "";
        String content = value;
        String stripped = value.stripLeading();
        // Prevent CSV Formula Injection in Excel / Calc:
        // Prefixes =, +, -, @, Tab, Carriage Return can trigger arbitrary DDE formulas
        if (content.startsWith("=") || content.startsWith("+") || content.startsWith("-") || content.startsWith("@")
                || content.startsWith("\t") || content.startsWith("\r")
                || stripped.startsWith("=") || stripped.startsWith("+") || stripped.startsWith("-") || stripped.startsWith("@")) {
            content = "'" + content;
        }
        if (content.contains("\"") || content.contains(",") || content.contains("\n") || content.contains("\r")) {
            return "\"" + content.replace("\"", "\"\"") + "\"";
        }
        return content;
    }

    public PlatformSummaryDto getPlatformSummary() {
        Long totalRev = jdbc.queryForObject(
            "SELECT COALESCE(SUM(total_amount_vnd), 0) FROM ticketing.bookings WHERE status = 'CONFIRMED'",
            Long.class
        );
        long totalRevenueVnd = totalRev != null ? totalRev : 0L;

        Long totalTkt = jdbc.queryForObject(
            "SELECT count(t.id) FROM ticketing.tickets t JOIN ticketing.bookings b ON b.id = t.booking_id WHERE b.status = 'CONFIRMED'",
            Long.class
        );
        long totalTicketsSold = totalTkt != null ? totalTkt : 0L;

        Long totalBkg = jdbc.queryForObject(
            "SELECT count(id) FROM ticketing.bookings WHERE status = 'CONFIRMED'",
            Long.class
        );
        long totalConfirmedBookings = totalBkg != null ? totalBkg : 0L;

        Long totalChk = jdbc.queryForObject(
            "SELECT count(id) FROM ticketing.ticket_check_ins",
            Long.class
        );
        long totalCheckIns = totalChk != null ? totalChk : 0L;

        double averageOccupancyPercent = totalTicketsSold > 0
            ? Math.round((double) totalCheckIns * 1000.0 / totalTicketsSold) / 10.0
            : 0.0;

        return new PlatformSummaryDto(
            totalRevenueVnd,
            totalTicketsSold,
            totalConfirmedBookings,
            totalCheckIns,
            averageOccupancyPercent
        );
    }

    public List<EventReportDto> getEventReports() {
        var eventRows = jdbc.queryForList(
            "SELECT id, title, venue, starts_at, status FROM ticketing.events ORDER BY starts_at DESC"
        );

        List<EventReportDto> reports = new ArrayList<>();
        for (var row : eventRows) {
            UUID eventId = (UUID) row.get("id");
            String title = (String) row.get("title");
            String venue = (String) row.get("venue");
            Timestamp startsAt = (Timestamp) row.get("starts_at");
            String status = (String) row.get("status");

            // GA capacity
            Long gaCap = jdbc.queryForObject(
                "SELECT COALESCE(SUM(i.capacity), 0) FROM ticketing.inventory_ga i " +
                "JOIN ticketing.ticket_types tt ON tt.id = i.ticket_type_id " +
                "WHERE tt.event_id = ?",
                Long.class,
                eventId
            );
            long capacityGa = gaCap != null ? gaCap : 0L;

            // Seated capacity
            Long seatedCap = jdbc.queryForObject(
                "SELECT count(id) FROM ticketing.seats WHERE event_id = ?",
                Long.class,
                eventId
            );
            long capacitySeated = seatedCap != null ? seatedCap : 0L;
            long totalCapacity = capacityGa + capacitySeated;

            // GA sold
            Long gaSold = jdbc.queryForObject(
                "SELECT count(t.id) FROM ticketing.tickets t " +
                "JOIN ticketing.bookings b ON b.id = t.booking_id " +
                "JOIN ticketing.booking_items bi ON bi.id = t.booking_item_id " +
                "WHERE b.event_id = ? AND b.status = 'CONFIRMED' AND bi.seat_id IS NULL",
                Long.class,
                eventId
            );
            long soldGa = gaSold != null ? gaSold : 0L;

            // Seated sold
            Long seatedSold = jdbc.queryForObject(
                "SELECT count(t.id) FROM ticketing.tickets t " +
                "JOIN ticketing.bookings b ON b.id = t.booking_id " +
                "JOIN ticketing.booking_items bi ON bi.id = t.booking_item_id " +
                "WHERE b.event_id = ? AND b.status = 'CONFIRMED' AND bi.seat_id IS NOT NULL",
                Long.class,
                eventId
            );
            long soldSeated = seatedSold != null ? seatedSold : 0L;
            long totalSold = soldGa + soldSeated;

            // Revenue
            Long rev = jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_amount_vnd), 0) FROM ticketing.bookings WHERE event_id = ? AND status = 'CONFIRMED'",
                Long.class,
                eventId
            );
            long revenueVnd = rev != null ? rev : 0L;

            double occupancyPercent = totalCapacity > 0
                ? Math.round((double) totalSold * 1000.0 / totalCapacity) / 10.0
                : 0.0;

            reports.add(new EventReportDto(
                eventId,
                title,
                venue,
                startsAt != null ? startsAt.toInstant().toString() : null,
                status,
                totalCapacity,
                soldGa,
                soldSeated,
                totalSold,
                revenueVnd,
                occupancyPercent
            ));
        }

        return reports;
    }

    public String generateEventsCsv() {
        List<EventReportDto> reports = getEventReports();
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Microsoft Excel compatibility
        sb.append("\uFEFF");
        sb.append("Mã Sự Kiện,Tên Sự Kiện,Địa Điểm,Thời Gian,Trạng Thái,Sức Chứa,Vé GA Đã Bán,Vé Ghế Đã Bán,Tổng Vé Đã Bán,Doanh Thu (VNĐ),Tỷ Lệ Lấp Đầy (%)\n");

        for (var r : reports) {
            sb.append(sanitizeCsvField(r.eventId().toString())).append(",");
            sb.append(sanitizeCsvField(r.title())).append(",");
            sb.append(sanitizeCsvField(r.venue())).append(",");
            sb.append(sanitizeCsvField(r.startsAt())).append(",");
            sb.append(sanitizeCsvField(r.status())).append(",");
            sb.append(r.totalCapacity()).append(",");
            sb.append(r.soldGa()).append(",");
            sb.append(r.soldSeated()).append(",");
            sb.append(r.totalSold()).append(",");
            sb.append(r.revenueVnd()).append(",");
            sb.append(r.occupancyPercent()).append("%\n");
        }

        return sb.toString();
    }
}
