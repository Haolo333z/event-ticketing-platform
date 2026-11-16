package com.haolo.ticketing.organizer;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;

@Service
public class EventSecurityService {

    private final JdbcTemplate jdbc;

    public EventSecurityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID getOrganizerProfileId(UUID userId) {
        if (userId == null) return null;
        var list = jdbc.query(
            "SELECT id FROM ticketing.organizer_profiles WHERE user_id = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            userId
        );
        return list.isEmpty() ? null : list.getFirst();
    }

    public boolean isEventOwner(UUID eventId, DemoPrincipal principal) {
        if (principal == null || eventId == null) return false;
        if (principal.hasRole("ADMIN")) return true;

        var list = jdbc.query(
            "SELECT organizer_id FROM ticketing.events WHERE id = ?",
            (rs, rowNum) -> rs.getObject("organizer_id", UUID.class),
            eventId
        );
        if (list.isEmpty()) return false;

        UUID eventOrganizerId = list.getFirst();
        if (eventOrganizerId == null) return false;

        UUID userProfileId = getOrganizerProfileId(principal.accountId());
        return eventOrganizerId.equals(userProfileId);
    }

    public void assertEventOwner(UUID eventId, DemoPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        if (principal.hasRole("ADMIN")) {
            return;
        }

        var list = jdbc.query(
            "SELECT organizer_id FROM ticketing.events WHERE id = ?",
            (rs, rowNum) -> rs.getObject("organizer_id", UUID.class),
            eventId
        );
        if (list.isEmpty()) {
            throw new ApiException(404, "EVENT_NOT_FOUND", "Sự kiện không tồn tại");
        }

        UUID eventOrganizerId = list.getFirst();
        UUID userProfileId = getOrganizerProfileId(principal.accountId());

        if (eventOrganizerId == null || userProfileId == null || !eventOrganizerId.equals(userProfileId)) {
            throw new ApiException(403, "FORBIDDEN", "Bạn không có quyền quản lý sự kiện này");
        }
    }
}
