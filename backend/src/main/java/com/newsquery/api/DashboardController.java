package com.newsquery.api;

import com.newsquery.analytics.NotificationAnalytics;
import com.newsquery.service.NotificationAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Phase 7: 알림 시스템 대시보드 API
 * 실시간 통계 및 모니터링
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final NotificationAnalyticsService analyticsService;
    private static final String DEFAULT_USER_ID = "anonymous";

    public DashboardController(NotificationAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 사용자 알림 통계
     * GET /api/dashboard/notifications
     */
    @GetMapping("/notifications")
    public ResponseEntity<NotificationAnalytics> getUserNotificationStats() {
        NotificationAnalytics analytics = analyticsService.getAnalytics(DEFAULT_USER_ID);
        return ResponseEntity.ok(analytics);
    }

    /**
     * 시스템 전체 알림 통계
     * GET /api/dashboard/notifications/system
     */
    @GetMapping("/notifications/system")
    public ResponseEntity<NotificationAnalytics> getSystemNotificationStats() {
        NotificationAnalytics analytics = analyticsService.getSystemAnalytics();
        return ResponseEntity.ok(analytics);
    }

    /**
     * 건강 체크
     * GET /api/dashboard/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now(),
            "version", "Phase 7"
        ));
    }

    /**
     * 시스템 상태 상세 정보
     * GET /api/dashboard/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        NotificationAnalytics analytics = analyticsService.getSystemAnalytics();
        return ResponseEntity.ok(Map.of(
            "notifications", analytics.toMap(),
            "uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(),
            "memory", Map.of(
                "total", java.lang.Runtime.getRuntime().totalMemory(),
                "free", java.lang.Runtime.getRuntime().freeMemory(),
                "max", java.lang.Runtime.getRuntime().maxMemory()
            ),
            "timestamp", LocalDateTime.now()
        ));
    }
}
