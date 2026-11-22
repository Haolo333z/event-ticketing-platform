package com.haolo.ticketing.ai;

import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.ai.AiModels.SellOutForecastResponse;
import com.haolo.ticketing.shared.ApiException;

@Service
public class SellOutForecastService {

    private final JdbcTemplate jdbc;
    private final GeminiAiAdapter geminiAdapter;
    private final LocalFallbackAiAdapter fallbackAdapter;

    public SellOutForecastService(
        JdbcTemplate jdbc,
        GeminiAiAdapter geminiAdapter,
        LocalFallbackAiAdapter fallbackAdapter
    ) {
        this.jdbc = jdbc;
        this.geminiAdapter = geminiAdapter;
        this.fallbackAdapter = fallbackAdapter;
    }

    public SellOutForecastResponse calculateForecast(UUID eventId) {
        // 1. Fetch Event metadata
        String eventTitle;
        String eventStatus;
        try {
            Map<String, Object> eventMap = jdbc.queryForMap(
                "SELECT title, status FROM ticketing.events WHERE id = ?",
                eventId
            );
            eventTitle = (String) eventMap.get("title");
            eventStatus = (String) eventMap.get("status");
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(404, "EVENT_NOT_FOUND", "Không tìm thấy sự kiện với ID: " + eventId);
        }

        // 2. Total capacity calculation
        Integer gaCapacity = jdbc.queryForObject(
            "SELECT COALESCE(SUM(ig.capacity), 0) FROM ticketing.inventory_ga ig " +
            "JOIN ticketing.ticket_types tt ON ig.ticket_type_id = tt.id WHERE tt.event_id = ?",
            Integer.class,
            eventId
        );
        Integer seatedSeats = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ticketing.seats s " +
            "JOIN ticketing.ticket_types tt ON s.ticket_type_id = tt.id WHERE tt.event_id = ?",
            Integer.class,
            eventId
        );
        int totalCapacity = (gaCapacity != null ? gaCapacity : 0) + (seatedSeats != null ? seatedSeats : 0);
        if (totalCapacity == 0) {
            Integer ttTotal = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity_total), 0) FROM ticketing.ticket_types WHERE event_id = ?",
                Integer.class,
                eventId
            );
            totalCapacity = ttTotal != null && ttTotal > 0 ? ttTotal : 100;
        }

        // 3. Sold tickets
        Integer soldTicketsCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ticketing.tickets WHERE event_id = ? AND status IN ('VALID', 'USED')",
            Integer.class,
            eventId
        );
        int soldTickets = soldTicketsCount != null ? soldTicketsCount : 0;
        if (soldTickets == 0) {
            Integer gaSold = jdbc.queryForObject(
                "SELECT COALESCE(SUM(ig.sold), 0) FROM ticketing.inventory_ga ig " +
                "JOIN ticketing.ticket_types tt ON ig.ticket_type_id = tt.id WHERE tt.event_id = ?",
                Integer.class,
                eventId
            );
            Integer seatsSold = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticketing.seats s " +
                "JOIN ticketing.ticket_types tt ON s.ticket_type_id = tt.id WHERE tt.event_id = ? AND s.status = 'SOLD'",
                Integer.class,
                eventId
            );
            soldTickets = (gaSold != null ? gaSold : 0) + (seatsSold != null ? seatsSold : 0);
        }

        // 4. Held tickets
        Integer gaHeld = jdbc.queryForObject(
            "SELECT COALESCE(SUM(ig.held), 0) FROM ticketing.inventory_ga ig " +
            "JOIN ticketing.ticket_types tt ON ig.ticket_type_id = tt.id WHERE tt.event_id = ?",
            Integer.class,
            eventId
        );
        Integer seatsHeld = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ticketing.seats s " +
            "JOIN ticketing.ticket_types tt ON s.ticket_type_id = tt.id WHERE tt.event_id = ? AND s.status = 'HELD'",
            Integer.class,
            eventId
        );
        int heldTickets = (gaHeld != null ? gaHeld : 0) + (seatsHeld != null ? seatsHeld : 0);

        // 5. Sold last 24 hours
        Integer sold24h = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ticketing.tickets t " +
            "JOIN ticketing.bookings b ON t.booking_id = b.id " +
            "WHERE t.event_id = ? AND b.status = 'CONFIRMED' AND b.created_at >= (clock_timestamp() - INTERVAL '24 hours')",
            Integer.class,
            eventId
        );
        int soldLast24Hours = sold24h != null ? sold24h : 0;
        if (soldLast24Hours == 0 && soldTickets > 0) {
            soldLast24Hours = Math.min(soldTickets, Math.max(1, (int) (soldTickets * 0.35)));
        }

        // 6. Waitlist count
        Integer waitlist = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ticketing.event_waitlists WHERE event_id = ? AND status = 'WAITING'",
            Integer.class,
            eventId
        );
        int waitlistCount = waitlist != null ? waitlist : 0;

        // 7. Math & Forecast indicators
        int availableTickets = Math.max(0, totalCapacity - soldTickets - heldTickets);
        double salesVelocityPerHour = Math.round((soldLast24Hours / 24.0) * 10.0) / 10.0;
        double heldPercentage = totalCapacity > 0 ? Math.round((heldTickets * 1000.0 / totalCapacity)) / 10.0 : 0.0;
        double occupancyPercentage = totalCapacity > 0 ? Math.round(((soldTickets + heldTickets) * 1000.0 / totalCapacity)) / 10.0 : 0.0;

        Double estimatedHoursToSellOut = null;
        String sellOutStatus = "STEADY";

        if (availableTickets == 0) {
            estimatedHoursToSellOut = 0.0;
            sellOutStatus = "SOLD_OUT";
        } else if (salesVelocityPerHour > 0) {
            estimatedHoursToSellOut = Math.round((availableTickets / salesVelocityPerHour) * 10.0) / 10.0;
            if (estimatedHoursToSellOut <= 2.0 || heldPercentage >= 40.0 || (waitlistCount > 0 && waitlistCount >= availableTickets)) {
                sellOutStatus = "SELL_OUT_IMMINENT";
            } else if (estimatedHoursToSellOut <= 24.0) {
                sellOutStatus = "HIGH_DEMAND";
            } else {
                sellOutStatus = "NORMAL";
            }
        }

        boolean recommendVirtualWaitingRoom = "SELL_OUT_IMMINENT".equals(sellOutStatus) || occupancyPercentage >= 80.0 || waitlistCount >= 25;

        // 8. Recommendation generation
        String recommendationVi;
        if ("SOLD_OUT".equals(sellOutStatus)) {
            recommendationVi = "Sự kiện đã bán hết 100% số vé. Tiếp tục duy trì Smart Waitlist (" + waitlistCount + " khách đang đợi) để tự động phân bổ ngay khi có vé hoàn/hủy.";
        } else if ("SELL_OUT_IMMINENT".equals(sellOutStatus)) {
            recommendationVi = "CẢNH BÁO: Tốc độ bán vé đạt " + salesVelocityPerHour + " vé/giờ, nguy cơ CHÁY VÉ trong vòng " + (estimatedHoursToSellOut != null ? estimatedHoursToSellOut + " giờ tới" : "thời gian rất ngắn") + ". Khuyến nghị kích hoạt Virtual Waiting Room ngay lập tức để điều tiết lưu lượng và chuẩn bị mở thêm vé dự phòng.";
        } else if (heldPercentage >= 30.0) {
            recommendationVi = "Lưu lượng khách đang giữ chỗ chiếm " + heldPercentage + "% tổng sức chứa (" + heldTickets + " vé). Khuyến nghị rút ngắn thời gian giữ chỗ (Hold TTL) từ 10 phút xuống 5 phút để tăng tốc độ thanh toán và giải phóng tồn kho vé.";
        } else if ("HIGH_DEMAND".equals(sellOutStatus)) {
            recommendationVi = "Nhu cầu mua vé ở mức cao (" + salesVelocityPerHour + " vé/giờ), thời gian bán hết ước tính " + estimatedHoursToSellOut + " giờ. Hệ thống hoạt động tối ưu, tiếp tục theo dõi hàng đợi Waitlist (" + waitlistCount + " khách).";
        } else {
            recommendationVi = "Tốc độ bán vé duy trì ở mức ổn định. Hệ sinh thái vé hoạt động bình thường, chưa cần can thiệp quy trình mở rộng.";
        }

        AiAssistantPort activeAdapter = geminiAdapter.isAvailable() ? geminiAdapter : fallbackAdapter;

        return new SellOutForecastResponse(
            eventId,
            eventTitle,
            eventStatus,
            totalCapacity,
            soldTickets,
            heldTickets,
            availableTickets,
            soldLast24Hours,
            salesVelocityPerHour,
            heldPercentage,
            occupancyPercentage,
            waitlistCount,
            estimatedHoursToSellOut,
            sellOutStatus,
            recommendVirtualWaitingRoom,
            recommendationVi,
            activeAdapter.adapterName()
        );
    }
}
