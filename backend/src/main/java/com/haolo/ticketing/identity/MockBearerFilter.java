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
    private final DemoSettings settings;private final JdbcTemplate jdbc;private final ApiErrors errors;
    MockBearerFilter(DemoSettings settings,JdbcTemplate jdbc,ApiErrors errors){this.settings=settings;this.jdbc=jdbc;this.errors=errors;}
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String header=req.getHeader("Authorization");
        if(header!=null){
            String account=null;
            if(settings.enabled && header.startsWith("Bearer ")){
                String token=header.substring(7);
                if(token.equals(settings.customerToken))account="10000000-0000-4000-8000-000000000001";
                else if(token.equals(settings.adminToken))account="10000000-0000-4000-8000-000000000002";
            }
            if(account==null){errors.write(req,res,401,"UNAUTHENTICATED","Token không hợp lệ");return;}
            var users=jdbc.query("SELECT id,demo_role FROM ticketing.users WHERE id=?",(r,n)->new DemoPrincipal(r.getObject(1,UUID.class),r.getString(2)),UUID.fromString(account));
            if(users.isEmpty()){errors.write(req,res,401,"UNAUTHENTICATED","Tài khoản demo không tồn tại");return;}
            var user=users.getFirst();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role()))));
        }
        chain.doFilter(req,res);
    }
}
