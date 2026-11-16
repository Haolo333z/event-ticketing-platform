package com.haolo.ticketing.identity;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.haolo.ticketing.identity.AdminUserController.*;
import com.haolo.ticketing.shared.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserControllerTest {

    private JdbcTemplate jdbc;
    private AdminUserController controller;

    private final UUID adminId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final DemoPrincipal adminPrincipal = new DemoPrincipal(adminId, "ADMIN", List.of("ADMIN"));
    private final DemoPrincipal customerPrincipal = new DemoPrincipal(targetUserId, "CUSTOMER", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        controller = new AdminUserController(jdbc);
    }

    @Test
    void invalidPaginationThrowsValidationError() {
        assertThrows(ApiException.class, () -> controller.listUsers(-1, 20, null, null));
        assertThrows(ApiException.class, () -> controller.listUsers(0, 0, null, null));
        assertThrows(ApiException.class, () -> controller.listUsers(0, 101, null, null));
    }

    @Test
    void nonAdminCannotUpdateRoleOrStatus() {
        ApiException ex1 = assertThrows(ApiException.class, () ->
            controller.updateUserRole(targetUserId, new UpdateRoleRequest("STAFF", "GRANT"), customerPrincipal)
        );
        assertEquals(403, ex1.status);

        ApiException ex2 = assertThrows(ApiException.class, () ->
            controller.updateUserStatus(targetUserId, new UpdateStatusRequest("LOCKED"), customerPrincipal)
        );
        assertEquals(403, ex2.status);
    }

    @Test
    void adminCannotDemoteSelf() {
        // Admin attempts to revoke ADMIN role on their own user id
        ApiException ex = assertThrows(ApiException.class, () ->
            controller.updateUserRole(adminId, new UpdateRoleRequest("ADMIN", "REVOKE"), adminPrincipal)
        );
        assertEquals(400, ex.status);
        assertEquals("CANNOT_DEMOTE_SELF", ex.code);
        assertTrue(ex.getMessage().contains("Không thể tự tước quyền Quản trị viên"));
    }

    @Test
    void adminCannotLockSelf() {
        // Admin attempts to lock their own account
        ApiException ex = assertThrows(ApiException.class, () ->
            controller.updateUserStatus(adminId, new UpdateStatusRequest("LOCKED"), adminPrincipal)
        );
        assertEquals(400, ex.status);
        assertEquals("CANNOT_LOCK_SELF", ex.code);
        assertTrue(ex.getMessage().contains("Không thể tự khóa tài khoản"));
    }

    @Test
    void grantRoleSuccess() {
        when(jdbc.queryForList(contains("FROM ticketing.users WHERE id = ?"), eq(targetUserId)))
            .thenReturn(List.of(Map.of(
                "id", targetUserId,
                "display_name", "Target User",
                "email", "target@test.com",
                "phone", "0901234567",
                "demo_role", "CUSTOMER",
                "status", "ACTIVE",
                "created_at", new java.sql.Timestamp(System.currentTimeMillis())
            )));

        when(jdbc.queryForList(contains("SELECT role_id FROM ticketing.user_roles"), eq(String.class), eq(targetUserId)))
            .thenReturn(List.of("CUSTOMER", "STAFF"));

        ResponseEntity<UserSummary> res = controller.updateUserRole(
            targetUserId, new UpdateRoleRequest("STAFF", "GRANT"), adminPrincipal
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(targetUserId, res.getBody().id());

        // Verify user_roles insert
        verify(jdbc, times(1)).update(contains("INSERT INTO ticketing.user_roles"), eq(targetUserId), eq("STAFF"));
        // Verify audit log insert
        verify(jdbc, times(1)).update(contains("INSERT INTO ticketing.audit_logs"), eq(adminId), eq("ROLE_GRANT"), eq(targetUserId.toString()), any());
    }

    @Test
    void updateStatusSuccess() {
        when(jdbc.update(contains("UPDATE ticketing.users SET status = ?"), eq("LOCKED"), eq(targetUserId)))
            .thenReturn(1);

        when(jdbc.queryForList(contains("FROM ticketing.users WHERE id = ?"), eq(targetUserId)))
            .thenReturn(List.of(Map.of(
                "id", targetUserId,
                "display_name", "Target User",
                "email", "target@test.com",
                "phone", "0901234567",
                "demo_role", "CUSTOMER",
                "status", "LOCKED",
                "created_at", new java.sql.Timestamp(System.currentTimeMillis())
            )));

        ResponseEntity<UserSummary> res = controller.updateUserStatus(
            targetUserId, new UpdateStatusRequest("LOCKED"), adminPrincipal
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("LOCKED", res.getBody().status());

        // Verify audit log
        verify(jdbc, times(1)).update(contains("INSERT INTO ticketing.audit_logs"), eq(adminId), eq(targetUserId.toString()), any());
    }
}
