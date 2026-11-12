package com.haolo.ticketing.identity;

import com.haolo.ticketing.shared.ApiErrors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration @ConditionalOnWebApplication
public class SecurityConfiguration {
    @Bean org.springframework.security.core.userdetails.UserDetailsService noPasswordUsers(){
        return username->{throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Password login is disabled");};
    }
    @Bean SecurityFilterChain chain(HttpSecurity http,DemoSettings settings,JdbcTemplate jdbc,ApiErrors errors) throws Exception {
        return http.csrf(c->c.disable()).requestCache(c->c.disable()).httpBasic(c->c.disable()).formLogin(c->c.disable())
            .sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c->c.requestMatchers("/actuator/health","/error").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/events","/api/events/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/bookings").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET,"/api/me/bookings").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET,"/api/bookings/*").hasAnyRole("CUSTOMER","ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().denyAll())
            .exceptionHandling(c->c.authenticationEntryPoint((req,res,ex)->errors.write(req,res,401,"UNAUTHENTICATED","Cần đăng nhập"))
                .accessDeniedHandler((req,res,ex)->errors.write(req,res,403,"FORBIDDEN","Không có quyền truy cập")))
            .addFilterBefore(new MockBearerFilter(settings,jdbc,errors),UsernamePasswordAuthenticationFilter.class).build();
    }
}
