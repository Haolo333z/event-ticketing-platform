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
                    errors.write(req, res, 401, "UNAUTHENTICATED", "Tài khoản không tồn tại");
                    return;
                }
                var user = users.getFirst();
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())))
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
                errors.write(req, res, 401, "UNAUTHENTICATED", "Tài khoản demo không tồn tại");
                return;
            }
            var user = users.getFirst();
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())))
            );
        }
        chain.doFilter(req, res);
    }
}
