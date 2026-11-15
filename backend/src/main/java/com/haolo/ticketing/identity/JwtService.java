package com.haolo.ticketing.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {
    private final byte[] secretKeyBytes;
    private final long expirationSeconds;
    private final ObjectMapper mapper;

    public JwtService(
        @Value("${demo.jwt.secret:EventFlow-Secure-JWT-Token-Secret-Key-2026-Super-Secret-Key-256bit-Enterprise}") String secret,
        @Value("${demo.jwt.expiration-hours:24}") long expirationHours,
        ObjectMapper mapper
    ) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationHours * 3600;
        this.mapper = mapper;
    }

    public String generateToken(UUID userId, String email, String role, String displayName) {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = now + expirationSeconds;

            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", userId.toString());
            payload.put("email", email != null ? email : "");
            payload.put("role", role);
            payload.put("name", displayName != null ? displayName : "");
            payload.put("iat", now);
            payload.put("exp", exp);

            String headerEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(header));
            String payloadEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(payload));
            String signingInput = headerEncoded + "." + payloadEncoded;

            String signature = sign(signingInput);
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Error generating JWT token", e);
        }
    }

    public Optional<JwtClaims> validateAndExtract(String token) {
        try {
            if (token == null || token.isBlank()) return Optional.empty();
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();

            String signingInput = parts[0] + "." + parts[1];
            String expectedSig = sign(signingInput);
            if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> map = mapper.readValue(payloadBytes, Map.class);
            long exp = ((Number) map.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                return Optional.empty();
            }

            UUID userId = UUID.fromString((String) map.get("sub"));
            String email = (String) map.get("email");
            String role = (String) map.get("role");
            String name = (String) map.get("name");
            return Optional.of(new JwtClaims(userId, email, role, name));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public record JwtClaims(UUID userId, String email, String role, String displayName) {}
}
