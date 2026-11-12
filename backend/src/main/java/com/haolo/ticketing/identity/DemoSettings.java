package com.haolo.ticketing.identity;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DemoSettings {
    public final boolean enabled;
    public final boolean safeProfile;
    public final String customerToken;
    public final String adminToken;
    public DemoSettings(Environment env, @Value("${demo.auth.enabled:false}") boolean enabled,
            @Value("${demo.auth.customer-token:}") String customerToken, @Value("${demo.auth.admin-token:}") String adminToken) {
        var profiles=Arrays.asList(env.getActiveProfiles());
        this.safeProfile=!profiles.isEmpty() && profiles.stream().allMatch(p->p.equals("local")||p.equals("test"));
        if(enabled && !safeProfile) throw new IllegalStateException("Mock auth is allowed only in local/test profiles");
        if(enabled && (customerToken.isBlank()||adminToken.isBlank()||customerToken.equals(adminToken)))
            throw new IllegalStateException("Distinct demo fixtures are required");
        this.enabled=enabled; this.customerToken=customerToken; this.adminToken=adminToken;
    }
}
