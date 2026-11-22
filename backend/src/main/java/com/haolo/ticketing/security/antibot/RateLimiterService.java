package com.haolo.ticketing.security.antibot;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.shared.ApiException;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final JdbcTemplate jdbc;
    private final Map<String, ConcurrentLinkedDeque<Long>> requestWindows = new ConcurrentHashMap<>();
    private volatile boolean rateLimitingEnabled = true;

    public RateLimiterService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void setRateLimitingEnabled(boolean enabled) {
        this.rateLimitingEnabled = enabled;
    }

    public boolean isRateLimitingEnabled() {
        return rateLimitingEnabled;
    }

    public void assertAllowed(String clientIp, String endpoint, UUID accountId, int limit, int windowSeconds) {
        if (!rateLimitingEnabled) {
            return;
        }

        String key = (clientIp != null ? clientIp : "UNKNOWN") + ":" + endpoint;
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        ConcurrentLinkedDeque<Long> window = requestWindows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        // Evict older timestamps outside sliding window
        while (!window.isEmpty() && window.peekFirst() < windowStart) {
            window.pollFirst();
        }

        if (window.size() >= limit) {
            long retryAfterSeconds = Math.max(1, (window.peekFirst() + (windowSeconds * 1000L) - now) / 1000);
            log.warn("Rate limit exceeded for key {} on endpoint {} ({} requests in {}s, limit: {})", key, endpoint, window.size(), windowSeconds, limit);

            // Audit record asynchronously / safe
            try {
                jdbc.update(
                    "INSERT INTO ticketing.rate_limit_audits (id, client_ip, account_id, endpoint, request_count, window_started_at, blocked, blocked_until) " +
                    "VALUES (gen_random_uuid(), ?, ?, ?, ?, clock_timestamp(), true, ?)",
                    clientIp != null ? clientIp : "0.0.0.0",
                    accountId,
                    endpoint,
                    window.size() + 1,
                    Timestamp.from(Instant.now().plusSeconds(retryAfterSeconds))
                );
            } catch (Exception ex) {
                log.debug("Could not record rate limit audit: {}", ex.getMessage());
            }

            throw new ApiException(429, "RATE_LIMIT_EXCEEDED", "Bạn đang thao tác quá nhanh. Vui lòng thử lại sau " + retryAfterSeconds + " giây.");
        }

        window.addLast(now);
    }

    public void clear(String clientIp, String endpoint) {
        String key = (clientIp != null ? clientIp : "UNKNOWN") + ":" + endpoint;
        requestWindows.remove(key);
    }

    public void clearAll() {
        requestWindows.clear();
    }
}
