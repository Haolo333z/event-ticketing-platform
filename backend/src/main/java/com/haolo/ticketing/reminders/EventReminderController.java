package com.haolo.ticketing.reminders;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.reminders.EventReminderModels.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api")
public class EventReminderController {

    private final EventReminderService reminderService;

    public EventReminderController(EventReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping("/events/{eventId}/reminders")
    public ResponseEntity<CreateReminderResponse> registerReminder(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable UUID eventId,
        @RequestBody(required = false) CreateReminderRequest request
    ) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập để nhận thông báo mở bán");
        }
        int minutes = (request != null && request.remindBeforeMinutes() != null) ? request.remindBeforeMinutes() : 15;
        CreateReminderResponse res = reminderService.registerReminder(principal.accountId(), eventId, minutes);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/me/reminders")
    public ResponseEntity<Map<String, Object>> getMyReminders(
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        List<EventReminderDto> items = reminderService.getUserReminders(principal.accountId());
        return ResponseEntity.ok(Map.of("items", items, "total", items.size()));
    }

    @DeleteMapping("/events/{eventId}/reminders")
    public ResponseEntity<Map<String, Object>> cancelReminder(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable UUID eventId
    ) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        reminderService.cancelReminder(principal.accountId(), eventId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã hủy đăng ký thông báo mở bán"));
    }
}
