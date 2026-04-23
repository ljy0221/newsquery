package com.newsquery.api;

import com.newsquery.notification.Notification;
import com.newsquery.notification.NotificationService;
import com.newsquery.notification.NotificationManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 5.1 + Phase 6: 알림 관리 API
 * - Phase 5.1: 기존 알림 조회, 읽음 표시
 * - Phase 6: SSE 실시간 알림 스트리밍
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    private static final String DEFAULT_USER_ID = "anonymous";

    private final NotificationService notificationService;
    private final NotificationManager notificationManager;

    public NotificationController(
            NotificationService notificationService,
            NotificationManager notificationManager) {
        this.notificationService = notificationService;
        this.notificationManager = notificationManager;
    }

    /**
     * SSE 구독 엔드포인트 (Phase 6)
     *
     * GET /api/notifications/stream?user=anonymous
     *
     * 응답:
     * HTTP/1.1 200 OK
     * Content-Type: text/event-stream
     * Cache-Control: no-cache
     * Connection: keep-alive
     *
     * @param user 사용자 ID (기본값: "anonymous")
     * @param lastEventId 마지막 이벤트 ID (자동 재연결 시)
     * @return SseEmitter (스트림 응답)
     */
    @GetMapping("/stream")
    public SseEmitter subscribeToNotifications(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String user,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {

        logger.info("SSE 구독 시작: user={}, lastEventId={}", user, lastEventId);
        return notificationManager.subscribe(user, lastEventId);
    }

    /**
     * 테스트용 수동 알림 발송 (Phase 6)
     *
     * POST /api/notifications/test-send
     * Body: {
     *   "type": "PERFORMANCE_ALERT",
     *   "message": "Query response time exceeded 100ms"
     * }
     */
    @PostMapping("/test-send")
    public ResponseEntity<?> testSendNotification(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String user,
            @RequestBody Map<String, String> payload) {

        try {
            String type = payload.get("type");
            String message = payload.get("message");

            if (type == null || message == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "type과 message는 필수입니다"));
            }

            Notification notification = new Notification(
                user,
                type,
                message,
                LocalDateTime.now(),
                Map.of("testNotification", true, "sentAt", System.currentTimeMillis())
            );

            notificationManager.notify(user, notification);

            logger.info("테스트 알림 발송: user={}, type={}", user, type);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "notificationId", notification.getId(),
                "user", user,
                "type", type
            ));

        } catch (Exception e) {
            logger.error("테스트 알림 발송 실패: user={}, error={}", user, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "알림 발송 실패: " + e.getMessage()));
        }
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
        int activeSSESubscribers = notificationManager.getActiveSubscriberCount();

        return ResponseEntity.ok(Map.of(
            "total", total,
            "read", read,
            "unread", unread,
            "readPercentage", total > 0 ? Math.round((read * 100.0) / total) : 0,
            "activeSSESubscribers", activeSSESubscribers,
            "timestamp", System.currentTimeMillis()
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
