package com.haolo.ticketing.identity;

import com.haolo.ticketing.shared.ApiErrors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfiguration {

    @Bean
    org.springframework.security.core.userdetails.UserDetailsService noPasswordUsers() {
        return username -> {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Password login is handled via /api/auth/login");
        };
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, DemoSettings settings, JdbcTemplate jdbc, ApiErrors errors, JwtService jwtService) throws Exception {
        return http.csrf(c -> c.disable()).requestCache(c -> c.disable()).httpBasic(c -> c.disable()).formLogin(c -> c.disable())
            .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c -> c
                .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/metrics", "/actuator/metrics/**").access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(settings.safeProfile && authentication.get().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))))
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v3/events", "/api/v3/events/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/events/*/waiting-room/enter").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/events/*/waiting-room/simulate-surge").hasAnyRole("ADMIN", "ORGANIZER")
                .requestMatchers(HttpMethod.POST, "/api/events/*/waitlist", "/api/events/*/waitlist/cancel").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/me/waitlists").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/events/*/reminders", "/api/events/*/reminders/*").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/events/*/reminders").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/me/reminders").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/me/bookings/*/refund-progress").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/transfers/lookup/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/transfers/initiate", "/api/transfers/accept/*", "/api/transfers/*/cancel").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/me/transfers/**").authenticated()
                .requestMatchers("/api/me/profile", "/api/me/vat-defaults", "/api/me/change-password", "/api/me/sessions", "/api/me/sessions/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/resale/listings", "/api/resale/listings/*/reserve", "/api/resale/listings/*/settle", "/api/resale/listings/*/cancel").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/me/resale/listings").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/ai/status").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/ai/concierge/chat").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/ai/organizer/**").hasAnyRole("ORGANIZER", "ADMIN")
                .requestMatchers("/api/ai/admin/forecast/**").hasAnyRole("ADMIN", "ORGANIZER")
                .requestMatchers("/api/ai/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/security/anti-bot/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/contact/leads").permitAll()
                .requestMatchers("/api/organizer/onboard/**", "/api/organizer/onboard").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/vouchers/validate").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/bookings/*/apply-voucher").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/me/bookings/*/e-invoice").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/me/bookings/*/e-invoice").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/e-invoices/lookup/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/bookings/*/simulate-payment", "/api/bookings/*/cancel").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/bookings/*/tickets").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/tickets/*/qr", "/api/tickets/**").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/me/bookings").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/bookings/*").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/staff/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/organizer/venues", "/api/organizer/categories").permitAll()
                .requestMatchers("/api/organizer/profile").authenticated()
                .requestMatchers("/api/organizer/**").hasAnyRole("ORGANIZER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/payments/vnpay/return", "/api/payments/vnpay/ipn").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/checkout").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/bookings/*/payment-status").hasAnyRole("CUSTOMER", "ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(c -> c
                .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401, "UNAUTHENTICATED", "Cần đăng nhập"))
                .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403, "FORBIDDEN", "Không có quyền truy cập")))
            .addFilterBefore(new MockBearerFilter(settings, jdbc, errors, jwtService), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
