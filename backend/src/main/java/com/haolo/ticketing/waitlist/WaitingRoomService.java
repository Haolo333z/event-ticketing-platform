package com.haolo.ticketing.waitlist;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WaitingRoomService {

    public record WaitingRoomStatus(
        UUID eventId,
        String sessionId,
        String status, // ADMITTED or QUEUED
        int position,
        int estimatedWaitSeconds,
        int activeUsers,
        int maxActiveUsers,
        int queueLength,
        String admissionToken
    ) {}

    private static final class RoomState {
        volatile int maxActive;
        final Map<String, Instant> activeSessions = new ConcurrentHashMap<>();
        final ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
        final Map<String, String> admissionTokens = new ConcurrentHashMap<>();
        RoomState(int maxActive) { this.maxActive = maxActive; }
    }

    private final int defaultMaxActiveUsers;
    private final long sessionTtlSeconds;
    private final Map<UUID, RoomState> rooms = new ConcurrentHashMap<>();

    public WaitingRoomService(
        @Value("${waiting-room.max-active-users:50}") int defaultMaxActiveUsers,
        @Value("${waiting-room.session-ttl-seconds:180}") long sessionTtlSeconds
    ) {
        this.defaultMaxActiveUsers = Math.max(1, defaultMaxActiveUsers);
        this.sessionTtlSeconds = Math.max(10, sessionTtlSeconds);
    }

    public WaitingRoomStatus enter(UUID eventId, String requestedSessionId) {
        String sessionId = (requestedSessionId != null && !requestedSessionId.isBlank())
            ? requestedSessionId.trim()
            : UUID.randomUUID().toString();
        RoomState room = rooms.computeIfAbsent(eventId, k -> new RoomState(defaultMaxActiveUsers));
        synchronized (room) {
            pruneExpired(room);
            if (room.activeSessions.containsKey(sessionId)) {
                room.activeSessions.put(sessionId, Instant.now());
                return buildAdmitted(eventId, sessionId, room);
            }
            if (room.activeSessions.size() < room.maxActive && room.queue.isEmpty()) {
                room.activeSessions.put(sessionId, Instant.now());
                room.admissionTokens.putIfAbsent(sessionId, "WR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
                return buildAdmitted(eventId, sessionId, room);
            }
            if (!room.queue.contains(sessionId)) {
                room.queue.addLast(sessionId);
            }
            promoteFromQueue(room);
            if (room.activeSessions.containsKey(sessionId)) {
                return buildAdmitted(eventId, sessionId, room);
            }
            return buildQueued(eventId, sessionId, room);
        }
    }

    public WaitingRoomStatus status(UUID eventId, String sessionId) {
        return enter(eventId, sessionId);
    }

    public Map<String, Object> configureSurge(UUID eventId, int maxActiveUsers, int simulatedQueuedUsers) {
        int safeMax = Math.max(1, Math.min(maxActiveUsers, 1000));
        int safeSim = Math.max(0, Math.min(simulatedQueuedUsers, 200));
        RoomState room = rooms.computeIfAbsent(eventId, k -> new RoomState(safeMax));
        synchronized (room) {
            room.maxActive = safeMax;
            room.activeSessions.clear();
            room.queue.clear();
            room.admissionTokens.clear();
            Instant now = Instant.now();
            for (int i = 0; i < safeMax; i++) {
                String simId = "flash-active-" + i;
                room.activeSessions.put(simId, now);
                room.admissionTokens.put(simId, "WR-SIM-" + i);
            }
            for (int i = 0; i < safeSim; i++) {
                room.queue.addLast("flash-queued-" + i);
            }
            return Map.of(
                "eventId", eventId,
                "maxActiveUsers", room.maxActive,
                "activeUsers", room.activeSessions.size(),
                "queueLength", room.queue.size()
            );
        }
    }

    public void releaseSlot(UUID eventId, String sessionId) {
        RoomState room = rooms.get(eventId);
        if (room == null) return;
        synchronized (room) {
            room.activeSessions.remove(sessionId);
            room.admissionTokens.remove(sessionId);
            room.queue.remove(sessionId);
            promoteFromQueue(room);
        }
    }

    private void pruneExpired(RoomState room) {
        Instant cutoff = Instant.now().minusSeconds(sessionTtlSeconds);
        room.activeSessions.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        promoteFromQueue(room);
    }

    private void promoteFromQueue(RoomState room) {
        while (room.activeSessions.size() < room.maxActive && !room.queue.isEmpty()) {
            String next = room.queue.pollFirst();
            if (next != null) {
                room.activeSessions.put(next, Instant.now());
                room.admissionTokens.putIfAbsent(next, "WR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
            }
        }
    }

    private WaitingRoomStatus buildAdmitted(UUID eventId, String sessionId, RoomState room) {
        return new WaitingRoomStatus(
            eventId, sessionId, "ADMITTED", 0, 0,
            room.activeSessions.size(), room.maxActive, room.queue.size(),
            room.admissionTokens.getOrDefault(sessionId, "")
        );
    }

    private WaitingRoomStatus buildQueued(UUID eventId, String sessionId, RoomState room) {
        int pos = 1;
        for (String s : room.queue) {
            if (s.equals(sessionId)) break;
            pos++;
        }
        int estSeconds = Math.max(5, pos * 6);
        return new WaitingRoomStatus(
            eventId, sessionId, "QUEUED", pos, estSeconds,
            room.activeSessions.size(), room.maxActive, room.queue.size(), null
        );
    }
}
