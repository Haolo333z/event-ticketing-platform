package com.haolo.ticketing.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class EventStreamService {
    private static final Logger log = LoggerFactory.getLogger(EventStreamService.class);
    private static final long SSE_TIMEOUT_MS = 300_000L; // 5 minutes per connection

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID eventId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(eventId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> removeEmitter(eventId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of(
                    "eventId", eventId.toString(),
                    "connectedAt", Instant.now().toString()
                )));
        } catch (IOException | IllegalStateException ex) {
            cleanup.run();
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInventoryChanged(InventoryChangedEvent event) {
        if (event == null || event.eventId() == null) {
            return;
        }
        broadcast(event);
    }

    public int broadcast(InventoryChangedEvent event) {
        List<SseEmitter> list = emitters.get(event.eventId());
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Map<String, Object> payload = Map.of(
            "eventId", event.eventId().toString(),
            "reason", event.reason(),
            "bookingId", event.bookingId() != null ? event.bookingId().toString() : "",
            "occurredAt", (event.occurredAt() != null ? event.occurredAt() : Instant.now()).toString()
        );
        int delivered = 0;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                    .name("inventory-updated")
                    .data(payload));
                delivered++;
            } catch (IOException | IllegalStateException ex) {
                removeEmitter(event.eventId(), emitter);
                log.debug("Removed closed SSE subscriber for event {}", event.eventId());
            }
        }
        return delivered;
    }

    public int activeSubscribers(UUID eventId) {
        List<SseEmitter> list = emitters.get(eventId);
        return list == null ? 0 : list.size();
    }

    private void removeEmitter(UUID eventId, SseEmitter emitter) {
        emitters.computeIfPresent(eventId, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
