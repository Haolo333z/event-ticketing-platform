package com.haolo.ticketing.identity.oauth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthCallbackRequest;
import com.haolo.ticketing.identity.oauth.OAuthModels.OAuthUserInfo;
import com.haolo.ticketing.shared.ApiException;

@Component
public class GoogleOAuthAdapter implements OAuthIdentityPort {
    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthAdapter.class);

    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public GoogleOAuthAdapter(
        @Value("${GOOGLE_CLIENT_ID:}") String clientId,
        @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret,
        ObjectMapper mapper
    ) {
        this.clientId = clientId != null ? clientId.trim() : "";
        this.clientSecret = clientSecret != null ? clientSecret.trim() : "";
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String getClientId() {
        return clientId;
    }

    @Override
    public String provider() {
        return "GOOGLE";
    }

    @Override
    public boolean isAvailable() {
        return !clientId.isEmpty();
    }

    @Override
    public OAuthUserInfo authenticate(OAuthCallbackRequest request) {
        if (request == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Dữ liệu xác thực Google không hợp lệ");
        }

        String idToken = request.idToken() != null ? request.idToken().trim() : "";
        if (idToken.isEmpty()) {
            throw new ApiException(400, "INVALID_TOKEN", "Google ID Token không được để trống");
        }

        try {
            // Verify token with Google's public tokeninfo endpoint
            URI uri = URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken);
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();

            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Google tokeninfo returned HTTP {}", response.statusCode());
                throw new ApiException(401, "INVALID_GOOGLE_TOKEN", "Google ID Token không hợp lệ hoặc đã hết hạn");
            }

            JsonNode root = mapper.readTree(response.body());
            String sub = root.path("sub").asString("");
            String email = root.path("email").asString("");
            String name = root.path("name").asString("");
            String picture = root.path("picture").asString("");

            if (sub.isEmpty() || email.isEmpty()) {
                throw new ApiException(401, "INVALID_GOOGLE_TOKEN", "Không thể lấy thông tin danh tính từ Google");
            }

            // Verify audience matches configured clientId if available
            String aud = root.path("aud").asString("");
            if (!clientId.isEmpty() && !clientId.equals(aud)) {
                log.warn("Google token audience mismatch: expected {}, got {}", clientId, aud);
                throw new ApiException(401, "OAUTH_AUDIENCE_MISMATCH", "Google Client ID không khớp với hệ thống");
            }

            return new OAuthUserInfo(sub, email, name.isEmpty() ? email : name, picture);
        } catch (ApiException apiEx) {
            throw apiEx;
        } catch (Exception ex) {
            log.error("Error communicating with Google OAuth2 API: {}", ex.getMessage());
            throw new ApiException(502, "GOOGLE_AUTH_UNAVAILABLE", "Không thể kết nối đến máy chủ Google OAuth2");
        }
    }
}
