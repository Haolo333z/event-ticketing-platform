package com.haolo.ticketing.organizer;

import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.OrganizerModels.*;
import com.haolo.ticketing.shared.ApiException;
import static org.junit.jupiter.api.Assertions.*;

class OrganizerIntegrationTest {

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

    private static final String URL = "jdbc:postgresql://localhost:5432/ticketing_demo";
    private static final String USER = "ticketing";
    private static final String PASS = "1454158d66ab469493b8bbca1669b0681465533ede684524a1919126f88b3b0b";

    private JdbcTemplate jdbc;
    private OrganizerService service;
    private EventSecurityService securityService;

    private UUID orgAId;
    private UUID orgBId;
    private UUID staffId;
    private UUID custId;
    private UUID adminId;

    private DemoPrincipal orgAPrincipal;
    private DemoPrincipal orgBPrincipal;
    private DemoPrincipal adminPrincipal;

    private static boolean isDbAvailable() {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 5432), 150);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDbAvailable(), "PostgreSQL is not reachable on localhost:5432");
        var ds = new DriverManagerDataSource(URL, USER, PASS);
        jdbc = new JdbcTemplate(ds);
        securityService = new EventSecurityService(jdbc);
        service = new OrganizerService(jdbc, securityService);

        orgAId = UUID.randomUUID();
        orgBId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        custId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        // Seed test accounts
        seedUser(orgAId, "Organizer Alpha", "orga-" + orgAId + "@test.com", "ORGANIZER");
        seedUser(orgBId, "Organizer Beta", "orgb-" + orgBId + "@test.com", "ORGANIZER");
        seedUser(staffId, "Staff Gate 1", "staff-" + staffId + "@test.com", "STAFF");
        seedUser(custId, "Customer One", "cust-" + custId + "@test.com", "CUSTOMER");
        seedUser(adminId, "Super Admin", "admin-" + adminId + "@test.com", "ADMIN");

        orgAPrincipal = new DemoPrincipal(orgAId, "ORGANIZER", List.of("ORGANIZER"));
        orgBPrincipal = new DemoPrincipal(orgBId, "ORGANIZER", List.of("ORGANIZER"));
        adminPrincipal = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));
    }

    private void seedUser(UUID id, String name, String email, String role) {
        jdbc.update(
            "INSERT INTO ticketing.users (id, display_name, email, demo_role) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
            id, name, email, role
        );
        jdbc.update(
            "INSERT INTO ticketing.user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            id, role
        );
    }

    @Test
    void testOrganizerProfileCreationAndAutoRoleGrant() {
        OrganizerProfileRequest req = new OrganizerProfileRequest(
            "Công ty Sự kiện Sài Gòn",
            "contact@saigonevents.vn",
            "0909123456",
            "0312345678"
        );
        OrganizerProfile profile = service.saveProfile(orgAId, req);
        assertNotNull(profile);
        assertEquals("Công ty Sự kiện Sài Gòn", profile.organizationName());
        assertEquals("contact@saigonevents.vn", profile.contactEmail());
        assertEquals("0909123456", profile.phone());
        assertEquals("ACTIVE", profile.status());

        // Verify role ORGANIZER was auto-granted in user_roles
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.user_roles WHERE user_id = ? AND role_id = 'ORGANIZER'",
            Integer.class,
            orgAId
        );
        assertEquals(1, count);
    }

    @Test
    void testEventLifecycleDraftToPublished() {
        // 1. Tạo sự kiện DRAFT
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant saleStart = now.plus(1, ChronoUnit.HOURS);
        Instant saleEnd = now.plus(24, ChronoUnit.HOURS);
        Instant startsAt = now.plus(48, ChronoUnit.HOURS);

        CreateOrganizerEventRequest req = new CreateOrganizerEventRequest(
            "Đại nhạc hội Mùa Hè 2026",
            "Đêm nhạc quy mô lớn",
            "Sân vận động Mỹ Đình",
            null,
            "CONCERT",
            saleStart,
            saleEnd,
            startsAt
        );

        OrganizerEventDto event = service.createDraftEvent(orgAPrincipal, req);
        assertNotNull(event);
        assertEquals("DRAFT", event.status());
        assertEquals("Đại nhạc hội Mùa Hè 2026", event.title());

        // 2. Kiểm tra sự kiện DRAFT không hiển thị trên Public Catalog (SELECT WHERE status='PUBLISHED')
        Integer publicCount = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.events WHERE id = ? AND status = 'PUBLISHED'",
            Integer.class,
            event.id()
        );
        assertEquals(0, publicCount, "Sự kiện DRAFT không được hiển thị trên Public Catalog");

        // 3. Xuất bản khi chưa có loại vé -> Bắt buộc nhận lỗi 400
        ApiException exNoTicket = assertThrows(ApiException.class, () -> service.publishEvent(event.id(), orgAPrincipal));
        assertEquals(400, exNoTicket.status);
        assertEquals("VALIDATION_ERROR", exNoTicket.code);

        // 4. Thêm loại vé GA capacity 1000
        AddTicketTypeRequest gaReq = new AddTicketTypeRequest("Vé Phổ Thông GA", "GA", 250000L, 1000L, null);
        OrganizerEventDto eventWithTicket = service.addTicketType(event.id(), orgAPrincipal, gaReq);
        assertEquals(1, eventWithTicket.ticketTypes().size());
        assertEquals(1000L, eventWithTicket.totalCapacity());

        // 5. Xuất bản thành công -> chuyển sang PUBLISHED
        OrganizerEventDto published = service.publishEvent(event.id(), orgAPrincipal);
        assertEquals("PUBLISHED", published.status());

        // 6. Kiểm tra Public Catalog -> Sự kiện đã xuất hiện
        Integer publicPublishedCount = jdbc.queryForObject(
            "SELECT count(*) FROM ticketing.events WHERE id = ? AND status = 'PUBLISHED'",
            Integer.class,
            event.id()
        );
        assertEquals(1, publicPublishedCount, "Sự kiện sau khi xuất bản phải có mặt trên Public Catalog");
    }

    @Test
    void testSecurityIsolationBetweenOrganizers() {
        // Organizer A tạo Event A
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        CreateOrganizerEventRequest req = new CreateOrganizerEventRequest(
            "Sự kiện riêng của A",
            "Mô tả của A",
            "Hà Nội",
            null,
            "CONFERENCE",
            now.plus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            now.plus(3, ChronoUnit.HOURS)
        );
        OrganizerEventDto eventA = service.createDraftEvent(orgAPrincipal, req);

        // 1. Organizer B cố tình sửa sự kiện của A -> Nhận HTTP 403 Forbidden
        UpdateOrganizerEventRequest hackReq = new UpdateOrganizerEventRequest(
            "B hack sự kiện của A",
            "Mô tả bị đổi",
            "TP.HCM",
            null,
            "CONFERENCE",
            now.plus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            now.plus(3, ChronoUnit.HOURS)
        );
        ApiException exForbidden = assertThrows(ApiException.class, () -> service.updateEvent(eventA.id(), orgBPrincipal, hackReq));
        assertEquals(403, exForbidden.status);
        assertEquals("FORBIDDEN", exForbidden.code);

        // 2. Organizer B gọi getEventDetail của A -> Nhận 403
        assertThrows(ApiException.class, () -> service.getEventDetail(eventA.id(), orgBPrincipal));

        // 3. Organizer B gọi listMyEvents -> Danh sách KHÔNG chứa sự kiện của A
        List<OrganizerEventDto> listB = service.listMyEvents(orgBPrincipal);
        boolean containsEventA = listB.stream().anyMatch(e -> e.id().equals(eventA.id()));
        assertFalse(containsEventA, "Organizer B tuyệt đối không nhìn thấy sự kiện của Organizer A");

        // 4. Admin có quyền xem và sửa sự kiện của A (Bypass)
        OrganizerEventDto adminView = service.getEventDetail(eventA.id(), adminPrincipal);
        assertNotNull(adminView);
        assertEquals("Sự kiện riêng của A", adminView.title());
    }

    @Test
    void testStaffAssignmentAndRevocation() {
        // Tạo sự kiện
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        OrganizerEventDto event = service.createDraftEvent(
            orgAPrincipal,
            new CreateOrganizerEventRequest(
                "Sự kiện phân công Staff",
                "Mô tả",
                "Đà Nẵng",
                null,
                "SPORTS",
                now.plus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS)
            )
        );

        // 1. Phân công user có role STAFF -> Thành công
        AssignStaffRequest staffReq = new AssignStaffRequest(staffId, null);
        StaffAssignmentDto assigned = service.assignStaff(event.id(), orgAPrincipal, staffReq);
        assertNotNull(assigned);
        assertEquals(staffId, assigned.staffUserId());
        assertNull(assigned.revokedAt());

        // 2. Thử phân công user chỉ có role CUSTOMER -> Thất bại với lỗi 400 INVALID_STAFF_ROLE
        AssignStaffRequest custReq = new AssignStaffRequest(custId, null);
        ApiException exCust = assertThrows(ApiException.class, () -> service.assignStaff(event.id(), orgAPrincipal, custReq));
        assertEquals(400, exCust.status);
        assertEquals("INVALID_STAFF_ROLE", exCust.code);

        // 3. Kiểm tra danh sách Staff đang hoạt động
        List<StaffAssignmentDto> staffList = service.listStaffAssignments(event.id(), orgAPrincipal);
        assertEquals(1, staffList.size());
        assertEquals(staffId, staffList.getFirst().staffUserId());

        // 4. Thu hồi quyền nhân viên
        service.revokeStaff(event.id(), staffId, orgAPrincipal);
        List<StaffAssignmentDto> staffListAfter = service.listStaffAssignments(event.id(), orgAPrincipal);
        assertEquals(0, staffListAfter.size(), "Nhân viên sau khi bị thu hồi không còn trong danh sách active");
    }
}
