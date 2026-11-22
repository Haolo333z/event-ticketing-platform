package com.haolo.ticketing.commercial;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.haolo.ticketing.checkin.CheckInModels.*;
import com.haolo.ticketing.checkin.StaffCheckInController;
import com.haolo.ticketing.checkin.StaffCheckInService;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.security.antibot.AntiBotController;
import com.haolo.ticketing.security.antibot.AntiBotModels.ChallengeResponse;
import com.haolo.ticketing.security.antibot.AntiBotModels.VerifyChallengeResponse;
import com.haolo.ticketing.security.antibot.AntiBotService;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Cum3ControllersTest {

    private MockMvc mockMvc;
    private StaffCheckInService staffService;
    private AntiBotService antiBotService;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        staffService = mock(StaffCheckInService.class);
        antiBotService = mock(AntiBotService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new StaffCheckInController(staffService),
            new AntiBotController(antiBotService)
        ).build();
    }

    @Test
    void getOfflineManifest_returnsManifestJson() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(staffService.getOfflineManifest(any(), eq(eventId)))
            .thenReturn(new OfflineManifestDto(
                eventId,
                "Festival 2026",
                "2026-11-20T10:00:00Z",
                1,
                List.of(new OfflineTicketItem(
                    UUID.randomUUID(), "TCK-99", "Nguyen Van A", "GA", "Vé GA", "QR-TOKEN", false, null
                ))
            ));

        mockMvc.perform(get("/api/staff/events/" + eventId + "/offline-manifest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Festival 2026"))
            .andExpect(jsonPath("$.totalTickets").value(1))
            .andExpect(jsonPath("$.tickets[0].ticketCode").value("TCK-99"));
    }

    @Test
    void batchSync_returnsBatchResults() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(staffService.batchSync(any(), any()))
            .thenReturn(new BatchSyncResponse(
                eventId,
                "2026-11-20T10:00:00Z",
                1,
                1,
                0,
                List.of(new OfflineSyncResultItem("S-1", UUID.randomUUID(), "TCK-1", "SYNCED", "GATE_1", "2026-11-20T10:00:00Z", "Thành công"))
            ));

        String body = mapper.writeValueAsString(Map.of(
            "eventId", eventId.toString(),
            "items", List.of(Map.of("clientSyncId", "S-1", "ticketCode", "TCK-1"))
        ));

        mockMvc.perform(post("/api/staff/check-ins/batch-sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.syncedCount").value(1))
            .andExpect(jsonPath("$.results[0].status").value("SYNCED"));
    }

    @Test
    void antiBotChallengeAndVerify_endpointsWork() throws Exception {
        when(antiBotService.issueChallenge(any(), any(), any()))
            .thenReturn(new ChallengeResponse("CHAL-123", "Tính 1 + 2 = ?", 300));
        when(antiBotService.verifyChallenge(eq("CHAL-123"), eq("3"), any()))
            .thenReturn(new VerifyChallengeResponse(true, "ADMIT-123", "OK", 900));

        mockMvc.perform(post("/api/security/anti-bot/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.challengeToken").value("CHAL-123"))
            .andExpect(jsonPath("$.question").value("Tính 1 + 2 = ?"));

        mockMvc.perform(post("/api/security/anti-bot/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("challengeToken", "CHAL-123", "solution", "3"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.admissionToken").value("ADMIT-123"));
    }
}
