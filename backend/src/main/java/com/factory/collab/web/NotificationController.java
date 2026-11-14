package com.factory.collab.web;

import com.factory.collab.config.CurrentUser;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.service.DashboardService;
import com.factory.collab.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        return dashboardService.build(CurrentUser.get());
    }

    @GetMapping("/notifications")
    public List<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "30") int size) {
        return notificationService.list(CurrentUser.get(), unreadOnly, page, size);
    }

    @GetMapping("/notifications/count")
    public NotificationCount count() {
        return notificationService.count(CurrentUser.get());
    }

    @PostMapping("/notifications/{id}/read")
    public NotificationDto read(@PathVariable Long id) {
        return notificationService.markRead(id, CurrentUser.get());
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", notificationService.markAllRead(CurrentUser.get()));
    }
}
