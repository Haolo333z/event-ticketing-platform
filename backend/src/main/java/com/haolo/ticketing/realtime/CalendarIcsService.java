package com.haolo.ticketing.realtime;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.shared.ApiException;

@Service
public class CalendarIcsService {
    private static final DateTimeFormatter ICS_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;

    public CalendarIcsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String generateEventIcs(UUID eventId) {
        var rows = jdbc.queryForList(
            "SELECT id, title, description, venue, starts_at, event_format, online_join_url FROM ticketing.events WHERE id = ? AND status = 'PUBLISHED'",
            eventId
        );
        if (rows.isEmpty()) {
            throw ApiException.missing();
        }
        var row = rows.getFirst();
        Instant startsAt = ((Timestamp) row.get("starts_at")).toInstant();
        Instant endsAt = startsAt.plusSeconds(7200); // Default 2 hours event duration
        String title = escapeIcs((String) row.get("title"));
        String desc = (String) row.get("description");
        String format = (String) row.get("event_format");
        String onlineUrl = (String) row.get("online_join_url");
        String location = "ONLINE".equals(format) && onlineUrl != null && !onlineUrl.isBlank()
            ? onlineUrl
            : (String) row.get("venue");

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//EventFlow Enterprise V3//Ticketing Platform//VI\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:event-").append(eventId).append("@eventflow.vn\r\n");
        sb.append("DTSTAMP:").append(ICS_UTC.format(Instant.now())).append("\r\n");
        sb.append("DTSTART:").append(ICS_UTC.format(startsAt)).append("\r\n");
        sb.append("DTEND:").append(ICS_UTC.format(endsAt)).append("\r\n");
        sb.append("SUMMARY:").append(title).append("\r\n");
        sb.append("LOCATION:").append(escapeIcs(location)).append("\r\n");
        String fullDesc = (desc != null ? desc : "") + ("ONLINE".equals(format) && onlineUrl != null && !onlineUrl.isBlank() ? "\nLink tham du Online: " + onlineUrl : "");
        sb.append("DESCRIPTION:").append(escapeIcs(fullDesc)).append("\r\n");
        if (onlineUrl != null && !onlineUrl.isBlank()) {
            sb.append("URL:").append(onlineUrl.trim()).append("\r\n");
        }
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    static String escapeIcs(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;");
    }
}
