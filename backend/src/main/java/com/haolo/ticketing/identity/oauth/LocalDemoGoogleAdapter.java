package com.haolo.ticketing.identity.oauth;

import java.util.*;
import org.springframework.stereotype.Component;
import com.haolo.ticketing.identity.oauth.OAuthModels.*;
import com.haolo.ticketing.shared.ApiException;

@Component
public class LocalDemoGoogleAdapter implements OAuthIdentityPort {

    public static final List<OAuthDemoAccount> DEMO_ACCOUNTS = List.of(
        new OAuthDemoAccount(
            "google-demo-customer",
            "customer.google@eventflow.vn",
            "Nguyễn Văn Google",
            "https://api.dicebear.com/7.x/bottts/svg?seed=GoogleCustomer"
        ),
        new OAuthDemoAccount(
            "google-demo-vip",
            "vip.google@eventflow.vn",
            "Trần Thị VIP Google",
            "https://api.dicebear.com/7.x/bottts/svg?seed=GoogleVip"
        ),
        new OAuthDemoAccount(
            "google-demo-partner",
            "partner.google@eventflow.vn",
            "Lê Hoàng Google Partner",
            "https://api.dicebear.com/7.x/bottts/svg?seed=GooglePartner"
        )
    );

    @Override
    public String provider() {
        return "GOOGLE";
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available as local deterministic fallback
    }

    @Override
    public OAuthUserInfo authenticate(OAuthCallbackRequest request) {
        if (request == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Yêu cầu xác thực Google không hợp lệ");
        }

        // 1. Direct Demo Account Selection
        if (request.demoAccountId() != null && !request.demoAccountId().isBlank()) {
            for (OAuthDemoAccount acc : DEMO_ACCOUNTS) {
                if (acc.id().equalsIgnoreCase(request.demoAccountId().trim())
                    || acc.email().equalsIgnoreCase(request.demoAccountId().trim())) {
                    return new OAuthUserInfo("demo-sub-" + acc.id(), acc.email(), acc.name(), acc.avatarUrl());
                }
            }
        }

        // 2. Mock token / IdToken parsing
        String idToken = request.idToken() != null ? request.idToken().trim() : "";
        if (idToken.startsWith("demo-google-user:") || idToken.startsWith("mock-google-token:")) {
            String email = idToken.substring(idToken.indexOf(':') + 1).trim();
            if (!email.contains("@")) email = email + "@gmail.com";
            String name = email.split("@")[0];
            return new OAuthUserInfo("mock-sub-" + email.hashCode(), email, name, "https://api.dicebear.com/7.x/bottts/svg?seed=" + name);
        }

        // 3. Fallback to default demo customer
        OAuthDemoAccount def = DEMO_ACCOUNTS.get(0);
        return new OAuthUserInfo("demo-sub-" + def.id(), def.email(), def.name(), def.avatarUrl());
    }
}
