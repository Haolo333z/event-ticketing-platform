package com.haolo.ticketing.realtime;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.EventSecurityService;
import com.haolo.ticketing.shared.ApiException;

@Service
public class EventV3Service {

    public record TicketTypeV3(UUID id, String name, String mode, long priceVnd, boolean active) {}
    public record EventV3Item(
        UUID id,
        String title,
        String description,
        String venue,
        String categoryId,
        String eventFormat,
        String onlineJoinUrl,
        String saleStartAt,
        String saleEndAt,
        String startsAt,
        String status,
        long minPriceVnd,
        long maxPriceVnd,
        List<TicketTypeV3> ticketTypes
    ) {}
    public record EventV3Page(List<EventV3Item> items, int page, int size, long totalElements) {}

    public record UpdateFormatRequest(String eventFormat, String onlineJoinUrl) {}
    public record GenerateSeatMatrixRequest(UUID ticketTypeId, List<String> rows, int seatsPerRow) {}
    public record SeatMatrixResult(UUID eventId, UUID ticketTypeId, int createdCount, List<String> sampleLabels) {}

    private static final Set<String> VALID_FORMATS = Set.of("OFFLINE_SEATED", "OFFLINE_GA", "ONLINE");

    private final JdbcTemplate jdbc;
    private final EventSecurityService eventSecurity;

    public EventV3Service(JdbcTemplate jdbc, EventSecurityService eventSecurity) {
        this.jdbc = jdbc;
        this.eventSecurity = eventSecurity;
    }

    public EventV3Page discoverEvents(String q, String format, String categoryId, Long minPrice, Long maxPrice, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        if (format != null && !format.isBlank() && !VALID_FORMATS.contains(format.toUpperCase(Locale.ROOT))) {
            throw new ApiException(400, "VALIDATION_ERROR", "Hình thức sự kiện không hợp lệ");
        }

        StringBuilder where = new StringBuilder(" WHERE e.status = 'PUBLISHED'");
        List<Object> params = new ArrayList<>();

        if (format != null && !format.isBlank()) {
            where.append(" AND e.event_format = ?");
            params.add(format.toUpperCase(Locale.ROOT));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            where.append(" AND e.category_id = ?");
            params.add(categoryId.toUpperCase(Locale.ROOT));
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(e.title) LIKE ? OR LOWER(e.description) LIKE ? OR LOWER(e.venue) LIKE ?)");
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (minPrice != null) {
            where.append(" AND EXISTS (SELECT 1 FROM ticketing.ticket_types tt WHERE tt.event_id = e.id AND tt.active = true AND tt.price_vnd >= ?)");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            where.append(" AND EXISTS (SELECT 1 FROM ticketing.ticket_types tt WHERE tt.event_id = e.id AND tt.active = true AND tt.price_vnd <= ?)");
            params.add(maxPrice);
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM ticketing.events e" + where, Long.class, params.toArray());
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(safePage * safeSize);

        List<EventV3Item> items = jdbc.query(
            "SELECT e.id, e.title, e.description, e.venue, e.category_id, e.event_format, e.online_join_url, " +
                "e.sale_start_at, e.sale_end_at, e.starts_at, e.status " +
                "FROM ticketing.events e" + where + " ORDER BY e.starts_at, e.id LIMIT ? OFFSET ?",
            (rs, n) -> {
                UUID eventId = rs.getObject("id", UUID.class);
                List<TicketTypeV3> types = jdbc.query(
                    "SELECT id, name, mode, price_vnd, active FROM ticketing.ticket_types WHERE event_id = ? ORDER BY price_vnd, id",
                    (tr, tn) -> new TicketTypeV3(
                        tr.getObject("id", UUID.class),
                        tr.getString("name"),
                        tr.getString("mode"),
                        tr.getLong("price_vnd"),
                        tr.getBoolean("active")
                    ),
                    eventId
                );
                long minP = types.stream().filter(TicketTypeV3::active).mapToLong(TicketTypeV3::priceVnd).min().orElse(0L);
                long maxP = types.stream().filter(TicketTypeV3::active).mapToLong(TicketTypeV3::priceVnd).max().orElse(0L);
                return new EventV3Item(
                    eventId,
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("venue"),
                    rs.getString("category_id"),
                    rs.getString("event_format"),
                    rs.getString("online_join_url"),
                    ((Timestamp) rs.getTimestamp("sale_start_at")).toInstant().toString(),
                    ((Timestamp) rs.getTimestamp("sale_end_at")).toInstant().toString(),
                    ((Timestamp) rs.getTimestamp("starts_at")).toInstant().toString(),
                    rs.getString("status"),
                    minP,
                    maxP,
                    types
                );
            },
            queryParams.toArray()
        );
        return new EventV3Page(items, safePage, safeSize, total != null ? total : 0L);
    }

    @Transactional
    public Map<String, Object> updateEventFormat(UUID eventId, DemoPrincipal principal, UpdateFormatRequest req) {
        eventSecurity.assertEventOwner(eventId, principal);
        if (req == null || req.eventFormat() == null || !VALID_FORMATS.contains(req.eventFormat().toUpperCase(Locale.ROOT))) {
            throw new ApiException(400, "VALIDATION_ERROR", "eventFormat phải là OFFLINE_SEATED, OFFLINE_GA hoặc ONLINE");
        }
        String format = req.eventFormat().toUpperCase(Locale.ROOT);
        String url = req.onlineJoinUrl() != null ? req.onlineJoinUrl().trim() : null;
        if ("ONLINE".equals(format) && (url == null || (!url.startsWith("https://") && !url.startsWith("http://")))) {
            throw new ApiException(400, "VALIDATION_ERROR", "Sự kiện ONLINE cần đường dẫn tham dự hợp lệ (http/https)");
        }
        int updated = jdbc.update(
            "UPDATE ticketing.events SET event_format = ?, online_join_url = ? WHERE id = ?",
            format, url, eventId
        );
        if (updated == 0) {
            throw ApiException.missing();
        }
        return Map.of("eventId", eventId, "eventFormat", format, "onlineJoinUrl", url != null ? url : "");
    }

    @Transactional
    public SeatMatrixResult generateSeatMatrix(UUID eventId, DemoPrincipal principal, GenerateSeatMatrixRequest req) {
        eventSecurity.assertEventOwner(eventId, principal);
        if (req == null || req.ticketTypeId() == null || req.rows() == null || req.rows().isEmpty()
            || req.seatsPerRow() < 1 || req.seatsPerRow() > 50 || req.rows().size() > 26) {
            throw new ApiException(400, "VALIDATION_ERROR", "Cấu hình ma trận ghế không hợp lệ (tối đa 26 hàng x 50 ghế)");
        }
        var ttRows = jdbc.queryForList(
            "SELECT mode FROM ticketing.ticket_types WHERE id = ? AND event_id = ?",
            req.ticketTypeId(), eventId
        );
        if (ttRows.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy hạng vé thuộc sự kiện này");
        }
        if (!"SEATED".equals(ttRows.getFirst().get("mode"))) {
            throw new ApiException(400, "VALIDATION_ERROR", "Chỉ hạng vé SEATED mới tạo được ma trận sơ đồ ghế");
        }

        int created = 0;
        List<String> samples = new ArrayList<>();
        for (String rawRow : req.rows()) {
            String row = rawRow != null ? rawRow.trim().toUpperCase(Locale.ROOT) : "";
            if (row.isEmpty() || row.length() > 12 || !row.matches("[A-Z0-9_-]+")) {
                throw new ApiException(400, "VALIDATION_ERROR", "Tên hàng ghế không hợp lệ: " + rawRow);
            }
            for (int num = 1; num <= req.seatsPerRow(); num++) {
                String label = row + num;
                int inserted = jdbc.update(
                    "INSERT INTO ticketing.seats(id, event_id, ticket_type_id, label, row_label, seat_number) " +
                        "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (event_id, label) DO NOTHING",
                    UUID.randomUUID(), eventId, req.ticketTypeId(), label, row, num
                );
                if (inserted > 0) {
                    created++;
                    if (samples.size() < 10) {
                        samples.add(label);
                    }
                }
            }
        }
        jdbc.update("UPDATE ticketing.events SET event_format = 'OFFLINE_SEATED' WHERE id = ?", eventId);
        return new SeatMatrixResult(eventId, req.ticketTypeId(), created, samples);
    }
}
