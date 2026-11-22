package com.haolo.ticketing.identity.oauth;

import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthCallbackRequest;
import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthUserInfo;

/**
 * Hexagonal Port for Third-Party OAuth2 Providers.
 * Supports production adapters and deterministic local demo adapters.
 */
public interface OAuthIdentityPort {
    String provider();
    boolean isAvailable();
    OAuthUserInfo authenticate(OAuthCallbackRequest request);
}
