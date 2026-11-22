package com.haolo.ticketing.identity;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.haolo.ticketing.shared.ApiErrors;

final class MockBearerFilter extends OncePerRequestFilter {
    private final DemoSettings settings;
    private final JdbcTemplate jdbc;
    private final ApiErrors errors;
    private final JwtService jwtService;

    MockBearerFilter(DemoSettings settings, JdbcTemplate jdbc, ApiErrors errors, JwtService jwtService) {
        this.settings = settings;
        this.jdbc = jdbc;
        this.errors = errors;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ")) {
                if (isPublicEndpoint(req)) {
                    chain.doFilter(req, res);
                    return;
                }
                errors.write(req, res, 401, "UNAUTHENTICATED", "Token không hợp lệ");
                return;
            }
            String token = header.substring(7).trim();

            // 1. Try real JWT validation first
            var claimsOpt = jwtService != null ? jwtService.validateAndExtract(token) : Optional.<JwtService.JwtClaims>empty();
            if (claimsOpt.isPresent()) {
                var claims = claimsOpt.get();
                List<DemoPrincipal> users;
                try {
                    users = jdbc.query("SELECT id, demo_role FROM ticketing.users WHERE id = ?",
                        (r, n) -> new DemoPrincipal(r.getObject(1, UUID.class), r.getString(2)),
                        claims.userId());
                } catch (org.springframework.dao.DataAccessException ex) {
                    String state = com.haolo.ticketing.bookings.BookingTransactions.sqlState(ex);
                    boolean busy = ex instanceof org.springframework.dao.TransientDataAccessException || state != null && (state.startsWith("08") || Set.of("55P03", "57014", "40P01", "40001").contains(state));
                    errors.write(req, res, busy ? 503 : 500, busy ? "BOOKING_BUSY" : "INTERNAL_ERROR", "Chưa thể xác thực tài khoản; hãy thử lại");
                    return;
                }
                if (users.isEmpty()) {
                    if (isPublicEndpoint(req)) {
                        chain.doFilter(req, res);
                        return;
                    }
                    errors.write(req, res, 401, "UNAUTHENTICATED", "Tài khoản không tồn tại");
                    return;
                }
                var user = users.getFirst();

                // Fetch multi-roles from user_roles
                List<String> roles = Collections.emptyList();
                try {
                    roles = jdbc.query(
                        "SELECT role_id FROM ticketing.user_roles WHERE user_id = ? ORDER BY role_id",
                        (r, n) -> r.getString(1),
                        user.accountId()
                    );
                } catch (Exception ignored) {
                    // fallback if migration not yet applied
                }
                if (roles.isEmpty()) {
                    roles = claims.roles();
                }
                if (roles.isEmpty() && user.role() != null) {
                    roles = List.of(user.role());
                }
                List<String> priorityOrder = List.of("ADMIN", "ORGANIZER", "STAFF", "CUSTOMER");
                String primaryRole = priorityOrder.stream().filter(roles::contains).findFirst()
                    .orElse(user.role() != null ? user.role() : (!roles.isEmpty() ? roles.getFirst() : "CUSTOMER"));
                var principalWithRoles = new DemoPrincipal(user.accountId(), primaryRole, roles);

                List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();

                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principalWithRoles, null, authorities)
                );
                chain.doFilter(req, res);
                return;
            }

            // 2. Fall back to mock token if enabled
            String account = null;
            if (settings != null && settings.enabled) {
                if (token.equals(settings.customerToken)) account = "10000000-0000-4000-8000-000000000001";
                else if (token.equals(settings.adminToken)) account = "10000000-0000-4000-8000-000000000002";
            }
            if (account == null) {
                if (isPublicEndpoint(req)) {
                    chain.doFilter(req, res);
                    return;
                }
                errors.write(req, res, 401, "UNAUTHENTICATED", "Token không hợp lệ");
                return;
            }
            List<DemoPrincipal> users;
            try {
                users = jdbc.query("SELECT id, demo_role FROM ticketing.users WHERE id = ?",
                    (r, n) -> new DemoPrincipal(r.getObject(1, UUID.class), r.getString(2)),
                    UUID.fromString(account));
            } catch (org.springframework.dao.DataAccessException ex) {
                String state = com.haolo.ticketing.bookings.BookingTransactions.sqlState(ex);
                boolean busy = ex instanceof org.springframework.dao.TransientDataAccessException || state != null && (state.startsWith("08") || Set.of("55P03", "57014", "40P01", "40001").contains(state));
                errors.write(req, res, busy ? 503 : 500, busy ? "BOOKING_BUSY" : "INTERNAL_ERROR", "Chưa thể xác thực tài khoản; hãy thử lại");
                return;
            }
            if (users.isEmpty()) {
                if (isPublicEndpoint(req)) {
                    chain.doFilter(req, res);
                    return;
                }
                errors.write(req, res, 401, "UNAUTHENTICATED", "Tài khoản demo không tồn tại");
                return;
            }
            var user = users.getFirst();

            // Fetch multi-roles for mock token
            List<String> roles = Collections.emptyList();
            try {
                roles = jdbc.query(
                    "SELECT role_id FROM ticketing.user_roles WHERE user_id = ? ORDER BY role_id",
                    (r, n) -> r.getString(1),
                    user.accountId()
                );
            } catch (Exception ignored) {
                // fallback
            }
            if (roles.isEmpty() && user.role() != null) {
                roles = List.of(user.role());
            }
            List<String> priorityOrder = List.of("ADMIN", "ORGANIZER", "STAFF", "CUSTOMER");
            String primaryRole = priorityOrder.stream().filter(roles::contains).findFirst()
                .orElse(user.role() != null ? user.role() : (!roles.isEmpty() ? roles.getFirst() : "CUSTOMER"));
            var principalWithRoles = new DemoPrincipal(user.accountId(), primaryRole, roles);

            List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principalWithRoles, null, authorities)
            );
        }
        chain.doFilter(req, res);
    }

    private boolean isPublicEndpoint(HttpServletRequest req) {
        String m = req.getMethod();
        if ("OPTIONS".equalsIgnoreCase(m)) {
            return true;
        }
        String u = req.getRequestURI();
        if ("GET".equalsIgnoreCase(m)) {
            if (u.startsWith("/api/events") ||
                u.startsWith("/api/v3/events") ||
                u.startsWith("/api/categories") ||
                u.startsWith("/api/plans") ||
                u.startsWith("/api/organizer/venues") ||
                u.startsWith("/api/organizer/categories") ||
                u.startsWith("/api/e-invoices/lookup") ||
                u.startsWith("/api/transfers/lookup") ||
                u.startsWith("/api/ai/status") ||
                u.startsWith("/api/payments/vnpay/") ||
                u.startsWith("/actuator/health") ||
                u.startsWith("/error")) {
                return true;
            }
        }
        return u.startsWith("/api/auth/") ||
               u.startsWith("/api/security/anti-bot/") ||
               u.equals("/api/contact/leads") ||
               u.startsWith("/api/organizer/onboard") ||
               u.equals("/api/vouchers/validate") ||
               u.equals("/api/ai/concierge/chat") ||
               (u.contains("/waiting-room/enter") && "POST".equalsIgnoreCase(m));
    }
}
