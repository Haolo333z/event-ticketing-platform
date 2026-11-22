package com.haolo.ticketing.identity;

import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.identity.AdminLeadController.ConvertedOrganizerDto;
import com.haolo.ticketing.shared.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminLeadConversionTest {

    private JdbcTemplate jdbc;
    private AdminLeadController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        controller = new AdminLeadController(jdbc);
    }

    @Test
    void convertToOrganizerSuccessfulOneTouchAcid() {
        UUID adminId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        DemoPrincipal admin = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));

        Map<String, Object> leadRow = Map.of(
            "id", leadId,
            "full_name", "Nguyễn Văn Đối Tác",
            "email", "doitac@saokim.vn",
            "phone", "0912345678",
            "company_name", "Công ty CP Sự Kiện Sao Kim",
            "event_scale", "PROFESSIONAL",
            "saas_plan", "PROFESSIONAL",
            "message", "Cần hợp tác bán vé concert",
            "status", "NEW",
            "notes", "Tiềm năng cao"
        );

        when(jdbc.queryForList(contains("FROM ticketing.contact_leads WHERE id = ? FOR UPDATE"), eq(leadId)))
            .thenReturn(List.of(leadRow));

        // Existing user
        UUID existingUserId = UUID.randomUUID();
        when(jdbc.queryForList(contains("SELECT id FROM ticketing.users WHERE email = ?"), eq("doitac@saokim.vn")))
            .thenReturn(List.of(Map.of("id", existingUserId)));

        // No existing profile
        when(jdbc.queryForList(contains("SELECT id FROM ticketing.organizer_profiles WHERE user_id = ?"), eq(existingUserId)))
            .thenReturn(Collections.emptyList());

        ResponseEntity<ConvertedOrganizerDto> response = controller.convertToOrganizer(leadId, admin);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        ConvertedOrganizerDto dto = response.getBody();
        assertNotNull(dto);
        assertEquals("CONVERTED", dto.status());
        assertEquals("PROFESSIONAL", dto.saasPlan());
        assertEquals("doitac@saokim.vn", dto.email());

        // Verify role assignment
        verify(jdbc).update(contains("INSERT INTO ticketing.user_roles"), eq(existingUserId));
        // Verify profile creation
        verify(jdbc).update(contains("INSERT INTO ticketing.organizer_profiles"), any(UUID.class), eq(existingUserId),
            eq("Công ty CP Sự Kiện Sao Kim"), eq("doitac@saokim.vn"), eq("0912345678"), eq("PROFESSIONAL"));
        // Verify lead status updated
        verify(jdbc).update(contains("UPDATE ticketing.contact_leads SET status = 'CONVERTED'"), anyString(), eq(leadId));
        // Verify audit log
        verify(jdbc).update(contains("INSERT INTO ticketing.audit_logs"), eq(adminId), anyString(), anyString());
    }

    @Test
    void convertToOrganizerFailsWhenAlreadyConverted() {
        UUID adminId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        DemoPrincipal admin = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));

        Map<String, Object> leadRow = Map.of(
            "id", leadId,
            "status", "CONVERTED"
        );
        when(jdbc.queryForList(contains("FROM ticketing.contact_leads WHERE id = ? FOR UPDATE"), eq(leadId)))
            .thenReturn(List.of(leadRow));

        ApiException ex = assertThrows(ApiException.class, () -> controller.convertToOrganizer(leadId, admin));
        assertEquals(409, ex.status);
        assertEquals("LEAD_ALREADY_CONVERTED", ex.code);
    }
}
