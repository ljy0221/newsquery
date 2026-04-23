package com.newsquery.api;

import com.newsquery.notification.Notification;
import com.newsquery.notification.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 5.1: 알림 관리 API
 * 사용자의 알림 조회 및 읽음 표시
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private static final String DEFAULT_USER_ID = "anonymous";

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 알림 목록 조회
     * GET /api/notifications?limit=50
     */
    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam(defaultValue = "50") int limit) {
        List<Notification> notifications = notificationService.getNotifications(DEFAULT_USER_ID, limit);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 특정 알림 읽음 표시
     * PATCH /api/notifications/{id}/read
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable String id) {
        notificationService.markAsRead(DEFAULT_USER_ID, id);
        return ResponseEntity.ok(Map.of("status", "success", "notificationId", id));
    }

    /**
     * 알림 통계 조회
     * GET /api/notifications/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        List<Notification> all = notificationService.getNotifications(DEFAULT_USER_ID, 1000);
        long total = all.size();
        long read = all.stream().filter(Notification::isRead).count();
        long unread = total - read;

        return ResponseEntity.ok(Map.of(
            "total", total,
            "read", read,
            "unread", unread,
            "readPercentage", total > 0 ? Math.round((read * 100.0) / total) : 0
        ));
    }

    /**
     * 알림 종류별 조회
     * GET /api/notifications/by-type?type=PERFORMANCE_DEGRADATION
     */
    @GetMapping("/by-type")
    public ResponseEntity<?> getNotificationsByType(@RequestParam String type) {
        List<Notification> all = notificationService.getNotifications(DEFAULT_USER_ID, 1000);
        List<Notification> filtered = all.stream()
            .filter(n -> n.getType().equals(type))
            .toList();
        return ResponseEntity.ok(filtered);
    }
}
