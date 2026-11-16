package com.haolo.ticketing.identity;

import java.util.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.haolo.ticketing.shared.ApiErrors;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FourRoleSecurityTest {
    private JdbcTemplate jdbc;
    private ApiErrors errors;
    private JwtService jwtService;
    private MockBearerFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc = mock(JdbcTemplate.class);
        errors = mock(ApiErrors.class);
        jwtService = new JwtService("security-test-secret-key-must-be-long-enough-for-sha256-test", 900, new ObjectMapper());
        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"test"});
        DemoSettings settings = new DemoSettings(env, true, "demo-customer-token", "demo-admin-token");
        filter = new MockBearerFilter(settings, jdbc, errors, jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerTokenMapsToRoleCustomer() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "cust@eventflow.vn", "CUSTOMER", List.of("CUSTOMER"), "Cust User");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jdbc.query(contains("SELECT id, demo_role"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of(new DemoPrincipal(userId, "CUSTOMER")));
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of("CUSTOMER"));

        filter.doFilterInternal(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER")));
        assertFalse(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        DemoPrincipal principal = (DemoPrincipal) auth.getPrincipal();
        assertEquals("CUSTOMER", principal.role());
        assertTrue(principal.hasRole("CUSTOMER"));
        assertFalse(principal.hasRole("ADMIN"));
    }

    @Test
    void adminTokenMapsToRoleAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "admin@eventflow.vn", "ADMIN", List.of("ADMIN"), "Admin User");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jdbc.query(contains("SELECT id, demo_role"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of(new DemoPrincipal(userId, "ADMIN")));
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of("ADMIN"));

        filter.doFilterInternal(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        DemoPrincipal principal = (DemoPrincipal) auth.getPrincipal();
        assertEquals("ADMIN", principal.role());
        assertTrue(principal.hasRole("ADMIN"));
    }

    @Test
    void staffAndOrganizerRolesAreProperlyMapped() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "staff@eventflow.vn", "STAFF", List.of("STAFF"), "Staff Member");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jdbc.query(contains("SELECT id, demo_role"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of(new DemoPrincipal(userId, "STAFF")));
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of("STAFF"));

        filter.doFilterInternal(req, res, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF")));
        assertFalse(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void multiRoleAccountHasAllAuthorities() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "vip@eventflow.vn", "ORGANIZER", List.of("CUSTOMER", "ORGANIZER"), "Organizer VIP");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jdbc.query(contains("SELECT id, demo_role"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of(new DemoPrincipal(userId, "ORGANIZER")));
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(userId)))
            .thenReturn(List.of("CUSTOMER", "ORGANIZER"));

        filter.doFilterInternal(req, res, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        // Multi-role contains BOTH ROLE_CUSTOMER and ROLE_ORGANIZER
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER")));
        assertFalse(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        DemoPrincipal principal = (DemoPrincipal) auth.getPrincipal();
        assertTrue(principal.hasRole("CUSTOMER"));
        assertTrue(principal.hasRole("ORGANIZER"));
        assertFalse(principal.hasRole("ADMIN"));
    }

    @Test
    void mockTokenBackwardCompatibilityPreserved() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer demo-admin-token");
        UUID adminId = UUID.fromString("10000000-0000-4000-8000-000000000002");
        when(jdbc.query(contains("SELECT id, demo_role"), any(RowMapper.class), eq(adminId)))
            .thenReturn(List.of(new DemoPrincipal(adminId, "ADMIN")));
        when(jdbc.query(contains("SELECT role_id FROM ticketing.user_roles"), any(RowMapper.class), eq(adminId)))
            .thenReturn(List.of("ADMIN"));

        filter.doFilterInternal(req, res, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }
}
