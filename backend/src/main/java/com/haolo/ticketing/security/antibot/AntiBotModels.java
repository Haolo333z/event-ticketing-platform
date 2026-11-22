package com.haolo.ticketing.security.antibot;

import java.util.UUID;

public class AntiBotModels {

    public record ChallengeRequest(
        UUID eventId
    ) {}

    public record ChallengeResponse(
        String challengeToken,
        String question,
        long expiresInSeconds
    ) {}

    public record VerifyChallengeRequest(
        String challengeToken,
        String solution
    ) {}

    public record VerifyChallengeResponse(
        boolean verified,
        String admissionToken,
        String message,
        long expiresInSeconds
    ) {}

    public record RateLimitStatus(
        boolean allowed,
        int currentCount,
        int limit,
        long retryAfterSeconds
    ) {}
}
