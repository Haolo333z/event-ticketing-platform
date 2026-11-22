package com.haolo.ticketing.security.antibot;

import java.util.UUID;
import com.haolo.ticketing.security.antibot.AntiBotModels.ChallengeResponse;
import com.haolo.ticketing.security.antibot.AntiBotModels.VerifyChallengeResponse;

public interface AntiBotChallengePort {

    ChallengeResponse createChallenge(String clientIp, UUID accountId, UUID eventId);

    VerifyChallengeResponse verifyChallenge(String challengeToken, String solution, String clientIp);

    boolean validateAdmissionToken(String admissionToken, UUID eventId);
}
