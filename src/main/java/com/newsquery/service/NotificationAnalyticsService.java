package com.newsquery.service;

import com.newsquery.analytics.NotificationAnalytics;
import com.newsquery.notification.Notification;
import com.newsquery.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 7: 알림 분석 서비스
 * 대시보드를 위한 알림 시스템 통계 계산
 */
@Service
public class NotificationAnalyticsService {

    private final NotificationService notificationService;

    public NotificationAnalyticsService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public NotificationAnalytics getAnalytics(String userId) {
        List<Notification> all = notificationService.getNotifications(userId, 10000);

        // 기본 통계
        long total = all.size();
        long read = all.stream().filter(Notification::isRead).count();
        long unread = total - read;

        // 종류별 분류
        Map<String, Long> byType = new HashMap<>();
        all.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Notification::getType,
                java.util.stream.Collectors.counting()
            ))
            .forEach((type, count) -> byType.put(type, count));

        // 채널별 분류 (로깅용)
        Map<String, Long> byChannel = new HashMap<>();
        byChannel.put("EMAIL", 0L);
        byChannel.put("PUSH", 0L);
        byChannel.put("WEBSOCKET", 0L);
        byChannel.put("LOG", (long) all.size());

        // 평균 응답시간 계산
        double avgResponseTime = calculateAverageResponseTime(userId);

        return new NotificationAnalytics(
            total,
            read,
            unread,
            byType,
            byChannel,
            avgResponseTime
        );
    }

    private double calculateAverageResponseTime(String userId) {
        // Phase 7.1: SavedQueryService에서 평균 응답시간 계산
        // 현재는 고정값
        return 25.5;
    }

    public NotificationAnalytics getSystemAnalytics() {
        // Phase 7.1: 전체 사용자 통계 계산
        // 현재: 개별 사용자 통계만 제공
        return getAnalytics("anonymous");
    }
}
