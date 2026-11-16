package com.haolo.ticketing.organizer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.OrganizerModels.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class OrganizerService {

    private final JdbcTemplate jdbc;
    private final EventSecurityService securityService;

    public OrganizerService(JdbcTemplate jdbc, EventSecurityService securityService) {
        this.jdbc = jdbc;
        this.securityService = securityService;
    }

    public OrganizerProfile getProfile(UUID userId) {
        if (userId == null) return null;
        var list = jdbc.query(
            "SELECT id, user_id, organization_name, contact_email, phone, tax_code, status, created_at, updated_at " +
            "FROM ticketing.organizer_profiles WHERE user_id = ?",
            this::mapOrganizerProfile,
            userId
        );
        return list.isEmpty() ? null : list.getFirst();
    }

    @Transactional
    public OrganizerProfile saveProfile(UUID userId, OrganizerProfileRequest req) {
        if (userId == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        if (req == null || req.organizationName() == null || req.organizationName().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tên tổ chức/doanh nghiệp không được để trống");
        }
        if (req.contactEmail() == null || req.contactEmail().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Email liên hệ không được để trống");
        }
        if (req.phone() == null || req.phone().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Số điện thoại liên hệ không được để trống");
        }

        OrganizerProfile existing = getProfile(userId);
        if (existing != null) {
            jdbc.update(
                "UPDATE ticketing.organizer_profiles SET organization_name = ?, contact_email = ?, phone = ?, tax_code = ?, updated_at = clock_timestamp() WHERE user_id = ?",
                req.organizationName().trim(),
                req.contactEmail().trim().toLowerCase(),
                req.phone().trim(),
                req.taxCode() != null ? req.taxCode().trim() : null,
                userId
            );
        } else {
            UUID newId = UUID.randomUUID();
            jdbc.update(
                "INSERT INTO ticketing.organizer_profiles (id, user_id, organization_name, contact_email, phone, tax_code) VALUES (?, ?, ?, ?, ?, ?)",
                newId,
                userId,
                req.organizationName().trim(),
                req.contactEmail().trim().toLowerCase(),
                req.phone().trim(),
                req.taxCode() != null ? req.taxCode().trim() : null
            );
        }

        // Tự động gán vai trò ORGANIZER trong user_roles nếu chưa có
        jdbc.update(
            "INSERT INTO ticketing.user_roles (user_id, role_id) VALUES (?, 'ORGANIZER') ON CONFLICT DO NOTHING",
            userId
        );

        return getProfile(userId);
    }

    public List<VenueDto> listVenues() {
        return jdbc.query(
            "SELECT id, name, address, city, country, capacity, seating_layout_json::text AS layout FROM ticketing.venues ORDER BY name",
            (rs, rowNum) -> new VenueDto(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("city"),
                rs.getString("country"),
                rs.getInt("capacity"),
                rs.getString("layout")
            )
        );
    }

    public List<EventCategoryDto> listCategories() {
        return jdbc.query(
            "SELECT id, name, description, icon_url FROM ticketing.event_categories ORDER BY id",
            (rs, rowNum) -> new EventCategoryDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("icon_url")
            )
        );
    }

    public List<OrganizerEventDto> listMyEvents(DemoPrincipal principal) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");

        String sql;
        List<Object> params = new ArrayList<>();

        if (principal.hasRole("ADMIN")) {
            sql = "SELECT id, title, description, venue, venue_id, category_id, organizer_id, sale_start_at, sale_end_at, starts_at, status, created_at " +
                  "FROM ticketing.events ORDER BY starts_at DESC";
        } else {
            UUID profileId = securityService.getOrganizerProfileId(principal.accountId());
            if (profileId == null) {
                return Collections.emptyList();
            }
            sql = "SELECT id, title, description, venue, venue_id, category_id, organizer_id, sale_start_at, sale_end_at, starts_at, status, created_at " +
                  "FROM ticketing.events WHERE organizer_id = ? ORDER BY starts_at DESC";
            params.add(profileId);
        }

        return jdbc.query(sql, (rs, rowNum) -> {
            UUID eventId = rs.getObject("id", UUID.class);
            List<TicketTypeDto> types = fetchTicketTypes(eventId);
            long totalCap = types.stream().mapToLong(t -> t.capacity() != null ? t.capacity() : 0).sum();
            long sold = computeSoldTickets(eventId);

            return new OrganizerEventDto(
                eventId,
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("venue"),
                rs.getObject("venue_id", UUID.class),
                rs.getString("category_id"),
                rs.getObject("organizer_id", UUID.class),
                rs.getTimestamp("sale_start_at").toInstant(),
                rs.getTimestamp("sale_end_at").toInstant(),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getString("status"),
                types,
                totalCap,
                sold,
                rs.getTimestamp("created_at").toInstant()
            );
        }, params.toArray());
    }

    public OrganizerEventDto getEventDetail(UUID eventId, DemoPrincipal principal) {
        securityService.assertEventOwner(eventId, principal);

        var list = jdbc.query(
            "SELECT id, title, description, venue, venue_id, category_id, organizer_id, sale_start_at, sale_end_at, starts_at, status, created_at " +
            "FROM ticketing.events WHERE id = ?",
            (rs, rowNum) -> {
                List<TicketTypeDto> types = fetchTicketTypes(eventId);
                long totalCap = types.stream().mapToLong(t -> t.capacity() != null ? t.capacity() : 0).sum();
                long sold = computeSoldTickets(eventId);

                return new OrganizerEventDto(
                    eventId,
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("venue"),
                    rs.getObject("venue_id", UUID.class),
                    rs.getString("category_id"),
                    rs.getObject("organizer_id", UUID.class),
                    rs.getTimestamp("sale_start_at").toInstant(),
                    rs.getTimestamp("sale_end_at").toInstant(),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getString("status"),
                    types,
                    totalCap,
                    sold,
                    rs.getTimestamp("created_at").toInstant()
                );
            },
            eventId
        );

        if (list.isEmpty()) {
            throw new ApiException(404, "EVENT_NOT_FOUND", "Sự kiện không tồn tại");
        }
        return list.getFirst();
    }

    @Transactional
    public OrganizerEventDto createDraftEvent(DemoPrincipal principal, CreateOrganizerEventRequest req) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        validateEventTimes(req.saleStartAt(), req.saleEndAt(), req.startsAt());

        if (req.title() == null || req.title().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tiêu đề sự kiện không được để trống");
        }

        String venueName = req.venue();
        UUID venueId = req.venueId();
        if (venueId != null) {
            var v = jdbc.query("SELECT name FROM ticketing.venues WHERE id = ?", (rs, rowNum) -> rs.getString("name"), venueId);
            if (!v.isEmpty() && (venueName == null || venueName.isBlank())) {
                venueName = v.getFirst();
            }
        }
        if (venueName == null || venueName.isBlank()) {
            venueName = "Địa điểm đang cập nhật";
        }

        UUID organizerProfileId = securityService.getOrganizerProfileId(principal.accountId());
        if (organizerProfileId == null) {
            // Tự động tạo hồ sơ Organizer tối thiểu cho tài khoản hiện tại
            var userRows = jdbc.queryForList("SELECT display_name, email FROM ticketing.users WHERE id = ?", principal.accountId());
            String dispName = !userRows.isEmpty() && userRows.getFirst().get("display_name") != null ? (String) userRows.getFirst().get("display_name") : "Nhà tổ chức";
            String email = !userRows.isEmpty() && userRows.getFirst().get("email") != null ? (String) userRows.getFirst().get("email") : "organizer@eventflow.vn";

            OrganizerProfile profile = saveProfile(
                principal.accountId(),
                new OrganizerProfileRequest(
                    dispName,
                    email,
                    "0900000000",
                    null
                )
            );
            organizerProfileId = profile.id();
        }

        UUID eventId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ticketing.events (id, title, description, venue, venue_id, category_id, organizer_id, sale_start_at, sale_end_at, starts_at, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT')",
            eventId,
            req.title().trim(),
            req.description() != null ? req.description().trim() : "",
            venueName.trim(),
            venueId,
            req.categoryId() != null && !req.categoryId().isBlank() ? req.categoryId().trim() : null,
            organizerProfileId,
            Timestamp.from(req.saleStartAt()),
            Timestamp.from(req.saleEndAt()),
            Timestamp.from(req.startsAt())
        );

        return getEventDetail(eventId, principal);
    }

    @Transactional
    public OrganizerEventDto updateEvent(UUID eventId, DemoPrincipal principal, UpdateOrganizerEventRequest req) {
        securityService.assertEventOwner(eventId, principal);
        validateEventTimes(req.saleStartAt(), req.saleEndAt(), req.startsAt());

        if (req.title() == null || req.title().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tiêu đề sự kiện không được để trống");
        }

        String venueName = req.venue();
        UUID venueId = req.venueId();
        if (venueId != null) {
            var v = jdbc.query("SELECT name FROM ticketing.venues WHERE id = ?", (rs, rowNum) -> rs.getString("name"), venueId);
            if (!v.isEmpty() && (venueName == null || venueName.isBlank())) {
                venueName = v.getFirst();
            }
        }
        if (venueName == null || venueName.isBlank()) {
            venueName = "Địa điểm đang cập nhật";
        }

        jdbc.update(
            "UPDATE ticketing.events SET title = ?, description = ?, venue = ?, venue_id = ?, category_id = ?, " +
            "sale_start_at = ?, sale_end_at = ?, starts_at = ? WHERE id = ?",
            req.title().trim(),
            req.description() != null ? req.description().trim() : "",
            venueName.trim(),
            venueId,
            req.categoryId() != null && !req.categoryId().isBlank() ? req.categoryId().trim() : null,
            Timestamp.from(req.saleStartAt()),
            Timestamp.from(req.saleEndAt()),
            Timestamp.from(req.startsAt()),
            eventId
        );

        return getEventDetail(eventId, principal);
    }

    @Transactional
    public OrganizerEventDto publishEvent(UUID eventId, DemoPrincipal principal) {
        securityService.assertEventOwner(eventId, principal);

        List<TicketTypeDto> types = fetchTicketTypes(eventId);
        if (types.isEmpty()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Sự kiện phải có ít nhất 1 loại vé trước khi xuất bản mở bán");
        }

        long totalCap = types.stream().mapToLong(t -> t.capacity() != null ? t.capacity() : 0).sum();
        if (totalCap <= 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tổng sức chứa sự kiện phải lớn hơn 0 trước khi xuất bản");
        }

        jdbc.update("UPDATE ticketing.events SET status = 'PUBLISHED' WHERE id = ?", eventId);
        return getEventDetail(eventId, principal);
    }

    @Transactional
    public OrganizerEventDto addTicketType(UUID eventId, DemoPrincipal principal, AddTicketTypeRequest req) {
        securityService.assertEventOwner(eventId, principal);

        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tên hạng vé không được để trống");
        }
        String mode = req.mode() != null ? req.mode().trim().toUpperCase() : "GA";
        if (!Set.of("GA", "SEATED").contains(mode)) {
            throw new ApiException(400, "VALIDATION_ERROR", "Loại vé phải là GA hoặc SEATED");
        }
        if (req.priceVnd() < 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "Giá vé không được âm");
        }

        UUID typeId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ticketing.ticket_types (id, event_id, name, mode, price_vnd, active) VALUES (?, ?, ?, ?, ?, true)",
            typeId,
            eventId,
            req.name().trim(),
            mode,
            req.priceVnd()
        );

        if ("GA".equals(mode)) {
            long cap = req.capacity() != null ? req.capacity() : 0;
            if (cap <= 0) {
                throw new ApiException(400, "VALIDATION_ERROR", "Sức chứa vé GA phải lớn hơn 0");
            }
            jdbc.update("INSERT INTO ticketing.inventory_ga (ticket_type_id, capacity) VALUES (?, ?)", typeId, cap);
        } else {
            List<String> labels = req.seatLabels();
            if (labels == null || labels.isEmpty()) {
                throw new ApiException(400, "VALIDATION_ERROR", "Sự kiện vé ghế chỉ định phải cấu hình danh sách nhãn ghế");
            }
            Set<String> uniqueLabels = new HashSet<>();
            for (String label : labels) {
                if (label == null || label.isBlank()) continue;
                String normalized = label.trim();
                if (!uniqueLabels.add(normalized)) {
                    throw new ApiException(400, "DUPLICATE_SEAT_LABEL", "Trùng lặp nhãn ghế: " + normalized);
                }
                jdbc.update(
                    "INSERT INTO ticketing.seats (id, event_id, ticket_type_id, label, status) VALUES (gen_random_uuid(), ?, ?, ?, 'AVAILABLE')",
                    eventId,
                    typeId,
                    normalized
                );
            }
        }

        return getEventDetail(eventId, principal);
    }

    public List<StaffAssignmentDto> listStaffAssignments(UUID eventId, DemoPrincipal principal) {
        securityService.assertEventOwner(eventId, principal);

        return jdbc.query(
            "SELECT a.id, a.event_id, a.staff_user_id, u.display_name, u.email, a.assigned_by, a.assigned_at, a.revoked_at " +
            "FROM ticketing.event_staff_assignments a " +
            "JOIN ticketing.users u ON a.staff_user_id = u.id " +
            "WHERE a.event_id = ? AND a.revoked_at IS NULL " +
            "ORDER BY a.assigned_at DESC",
            (rs, rowNum) -> new StaffAssignmentDto(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("staff_user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getObject("assigned_by", UUID.class),
                rs.getTimestamp("assigned_at").toInstant(),
                rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant() : null
            ),
            eventId
        );
    }

    @Transactional
    public StaffAssignmentDto assignStaff(UUID eventId, DemoPrincipal principal, AssignStaffRequest req) {
        securityService.assertEventOwner(eventId, principal);
        if (req == null || (req.staffUserId() == null && (req.staffEmail() == null || req.staffEmail().isBlank()))) {
            throw new ApiException(400, "VALIDATION_ERROR", "Cần cung cấp ID hoặc Email của nhân viên soát vé");
        }

        // Tìm tài khoản
        List<Map<String, Object>> users;
        if (req.staffUserId() != null) {
            users = jdbc.queryForList("SELECT id, display_name, email FROM ticketing.users WHERE id = ?", req.staffUserId());
        } else {
            users = jdbc.queryForList("SELECT id, display_name, email FROM ticketing.users WHERE LOWER(email) = ?", req.staffEmail().trim().toLowerCase());
        }
        if (users.isEmpty()) {
            throw new ApiException(404, "USER_NOT_FOUND", "Không tìm thấy tài khoản nhân viên");
        }

        var userRow = users.getFirst();
        UUID staffUserId = (UUID) userRow.get("id");
        String displayName = (String) userRow.get("display_name");
        String email = (String) userRow.get("email");

        // Kiểm tra xem tài khoản có vai trò STAFF không
        Integer hasStaffRole = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.user_roles WHERE user_id = ? AND role_id = 'STAFF'",
            Integer.class,
            staffUserId
        );
        if (hasStaffRole == null || hasStaffRole == 0) {
            // Kiểm tra thêm demo_role
            String demoRole = jdbc.queryForObject("SELECT demo_role FROM ticketing.users WHERE id = ?", String.class, staffUserId);
            if (!"STAFF".equals(demoRole)) {
                throw new ApiException(400, "INVALID_STAFF_ROLE", "Tài khoản không có vai trò Nhân viên soát vé (STAFF)");
            }
        }

        // Thêm bản ghi phân công
        UUID assignId = UUID.randomUUID();
        try {
            jdbc.update(
                "INSERT INTO ticketing.event_staff_assignments (id, event_id, staff_user_id, assigned_by) VALUES (?, ?, ?, ?)",
                assignId,
                eventId,
                staffUserId,
                principal.accountId()
            );
        } catch (DuplicateKeyException ex) {
            throw new ApiException(409, "ALREADY_ASSIGNED", "Nhân viên này đã được phân công phụ trách sự kiện");
        }

        return new StaffAssignmentDto(
            assignId,
            eventId,
            staffUserId,
            displayName,
            email,
            principal.accountId(),
            Instant.now(),
            null
        );
    }

    @Transactional
    public void revokeStaff(UUID eventId, UUID staffUserId, DemoPrincipal principal) {
        securityService.assertEventOwner(eventId, principal);
        if (staffUserId == null) throw new ApiException(400, "VALIDATION_ERROR", "Thiếu staffUserId");

        int rows = jdbc.update(
            "UPDATE ticketing.event_staff_assignments SET revoked_at = clock_timestamp() WHERE event_id = ? AND staff_user_id = ? AND revoked_at IS NULL",
            eventId,
            staffUserId
        );
        if (rows == 0) {
            throw new ApiException(404, "ASSIGNMENT_NOT_FOUND", "Không tìm thấy phân công nhân viên đang hoạt động");
        }
    }

    public List<StaffCandidateDto> searchStaffCandidates(String query) {
        String filter = query != null ? "%" + query.trim().toLowerCase() + "%" : "%";
        return jdbc.query(
            "SELECT DISTINCT u.id, u.display_name, u.email, u.phone " +
            "FROM ticketing.users u " +
            "JOIN ticketing.user_roles ur ON u.id = ur.user_id " +
            "WHERE ur.role_id = 'STAFF' AND (LOWER(u.email) LIKE ? OR LOWER(u.display_name) LIKE ?) " +
            "ORDER BY u.display_name LIMIT 20",
            (rs, rowNum) -> new StaffCandidateDto(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("phone")
            ),
            filter,
            filter
        );
    }

    private List<TicketTypeDto> fetchTicketTypes(UUID eventId) {
        return jdbc.query(
            "SELECT id, name, mode, price_vnd, active FROM ticketing.ticket_types WHERE event_id = ? ORDER BY id",
            (rs, rowNum) -> {
                UUID typeId = rs.getObject("id", UUID.class);
                String mode = rs.getString("mode");
                Long capacity = null;
                Integer available = null;
                List<SeatDto> seats = Collections.emptyList();

                if ("GA".equals(mode)) {
                    var gaRows = jdbc.query(
                        "SELECT capacity, capacity - held_count - sold_count AS avail FROM ticketing.inventory_ga WHERE ticket_type_id = ?",
                        (r, n) -> Map.of("cap", r.getLong("capacity"), "avail", r.getInt("avail")),
                        typeId
                    );
                    if (!gaRows.isEmpty()) {
                        capacity = (Long) gaRows.getFirst().get("cap");
                        available = (Integer) gaRows.getFirst().get("avail");
                    }
                } else if ("SEATED".equals(mode)) {
                    seats = jdbc.query(
                        "SELECT id, label, status FROM ticketing.seats WHERE ticket_type_id = ? ORDER BY label",
                        (r, n) -> new SeatDto(r.getObject("id", UUID.class), r.getString("label"), r.getString("status")),
                        typeId
                    );
                    capacity = (long) seats.size();
                    available = (int) seats.stream().filter(s -> "AVAILABLE".equals(s.status())).count();
                }

                return new TicketTypeDto(
                    typeId,
                    rs.getString("name"),
                    mode,
                    rs.getLong("price_vnd"),
                    rs.getBoolean("active"),
                    capacity,
                    available,
                    seats
                );
            },
            eventId
        );
    }

    private long computeSoldTickets(UUID eventId) {
        Integer gaSold = jdbc.queryForObject(
            "SELECT coalesce(sum(sold_count), 0) FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON g.ticket_type_id = t.id WHERE t.event_id = ?",
            Integer.class,
            eventId
        );
        Integer seatedSold = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.seats WHERE event_id = ? AND status = 'SOLD'",
            Integer.class,
            eventId
        );
        return (gaSold != null ? gaSold : 0) + (seatedSold != null ? seatedSold : 0);
    }

    private void validateEventTimes(Instant saleStart, Instant saleEnd, Instant startsAt) {
        if (saleStart == null || saleEnd == null || startsAt == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thời gian mở bán, kết thúc bán và bắt đầu sự kiện không được để trống");
        }
        if (!saleStart.isBefore(saleEnd)) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thời gian mở bán phải trước thời gian kết thúc bán");
        }
        if (saleEnd.isAfter(startsAt)) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thời gian kết thúc bán phải trước hoặc bằng thời gian bắt đầu sự kiện");
        }
    }

    private OrganizerProfile mapOrganizerProfile(ResultSet rs, int rowNum) throws SQLException {
        return new OrganizerProfile(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("organization_name"),
            rs.getString("contact_email"),
            rs.getString("phone"),
            rs.getString("tax_code"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
