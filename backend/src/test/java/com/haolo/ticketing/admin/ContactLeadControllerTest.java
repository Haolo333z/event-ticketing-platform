package com.haolo.ticketing.admin;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.identity.AdminLeadController;
import com.haolo.ticketing.identity.ContactLeadController;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactLeadControllerTest {

    private JdbcTemplate jdbc;
    private ContactLeadController publicController;
    private AdminLeadController adminController;

    private final UUID adminId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();
    private final DemoPrincipal adminPrincipal = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));
    private final DemoPrincipal customerPrincipal = new DemoPrincipal(UUID.randomUUID(), "CUSTOMER", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        publicController = new ContactLeadController(jdbc);
        adminController = new AdminLeadController(jdbc);
    }

    @Test
    void submitLeadSuccessReturns201() {
        var req = new ContactLeadController.LeadRequest(
            "Nguyễn Doanh Nghiệp", "contact@corp.vn", "0912345678", "Tập đoàn ABC", "1000 - 5000", "Cần tư vấn bán vé"
        );

        ResponseEntity<ContactLeadController.LeadResponse> res = publicController.submitLead(req);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertNotNull(res.getBody().id());
        assertTrue(res.getBody().message().contains("ghi nhận"));

        verify(jdbc, times(1)).update(contains("INSERT INTO ticketing.contact_leads"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitLeadValidationFailure() {
        assertThrows(ApiException.class, () ->
            publicController.submitLead(new ContactLeadController.LeadRequest("", "valid@email.com", "0912345678", null, null, "Msg"))
        );
        assertThrows(ApiException.class, () ->
            publicController.submitLead(new ContactLeadController.LeadRequest("Name", "invalid-email", "0912345678", null, null, "Msg"))
        );
        assertThrows(ApiException.class, () ->
            publicController.submitLead(new ContactLeadController.LeadRequest("Name", "valid@email.com", "", null, null, "Msg"))
        );
    }

    @Test
    void nonAdminCannotUpdateLeadStatus() {
        ApiException ex = assertThrows(ApiException.class, () ->
            adminController.updateStatus(leadId, new AdminLeadController.UpdateLeadStatusRequest("CONTACTED", "Gọi lại"), customerPrincipal)
        );
        assertEquals(403, ex.status);
    }

    @Test
    void adminUpdateLeadStatusSuccess() {
        when(jdbc.update(contains("UPDATE ticketing.contact_leads SET status = ?"), eq("CONTACTED"), eq("Đã liên hệ qua điện thoại"), eq(leadId)))
            .thenReturn(1);

        Map<String, Object> map = new HashMap<>();
        map.put("id", leadId);
        map.put("full_name", "Khách Hàng A");
        map.put("email", "a@corp.vn");
        map.put("phone", "0909000111");
        map.put("company_name", "Công ty X");
        map.put("event_scale", "500");
        map.put("message", "Cần báo giá");
        map.put("status", "CONTACTED");
        map.put("notes", "Đã liên hệ qua điện thoại");
        map.put("created_at", new java.sql.Timestamp(System.currentTimeMillis()));
        map.put("updated_at", new java.sql.Timestamp(System.currentTimeMillis()));

        when(jdbc.queryForList(contains("FROM ticketing.contact_leads WHERE id = ?"), eq(leadId)))
            .thenReturn(List.of(map));

        ResponseEntity<AdminLeadController.ContactLead> res = adminController.updateStatus(
            leadId, new AdminLeadController.UpdateLeadStatusRequest("CONTACTED", "Đã liên hệ qua điện thoại"), adminPrincipal
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("CONTACTED", res.getBody().status());
        assertEquals("Đã liên hệ qua điện thoại", res.getBody().notes());

        // Verify audit log
        verify(jdbc, times(1)).update(contains("INSERT INTO ticketing.audit_logs"), eq(adminId), eq(leadId.toString()), anyString());
    }
}
