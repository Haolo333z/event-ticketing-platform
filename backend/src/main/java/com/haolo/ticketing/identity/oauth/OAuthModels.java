package com.haolo.ticketing.identity.oauth;

import java.util.List;
import java.util.UUID;

public final class OAuthModels {
    private OAuthModels() {}

    public record OAuthUserInfo(
        String providerUserId,
        String email,
        String name,
        String avatarUrl
    ) {}

    public record OAuthCallbackRequest(
        String code,
        String idToken,
        String demoAccountId
    ) {}

    public record OAuthDemoAccount(
        String id,
        String email,
        String name,
        String avatarUrl
    ) {}

    public record OAuthProviderConfigResponse(
        String provider,
        String mode, // "LIVE" or "DEMO"
        String clientId,
        List<OAuthDemoAccount> demoAccounts
    ) {}
}
