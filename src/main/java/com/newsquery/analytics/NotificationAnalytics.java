package com.newsquery.analytics;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 7: 알림 분석 데이터
 * 대시보드에서 표시할 알림 시스템의 통계
 */
public class NotificationAnalytics {
    private final long totalNotifications;
    private final long readNotifications;
    private final long unreadNotifications;
    private final Map<String, Long> notificationsByType;
    private final Map<String, Long> notificationsByChannel;
    private final double avgResponseTime;
    private final LocalDateTime lastUpdated;

    public NotificationAnalytics(
        long totalNotifications,
        long readNotifications,
        long unreadNotifications,
        Map<String, Long> notificationsByType,
        Map<String, Long> notificationsByChannel,
        double avgResponseTime
    ) {
        this.totalNotifications = totalNotifications;
        this.readNotifications = readNotifications;
        this.unreadNotifications = unreadNotifications;
        this.notificationsByType = notificationsByType;
        this.notificationsByChannel = notificationsByChannel;
        this.avgResponseTime = avgResponseTime;
        this.lastUpdated = LocalDateTime.now();
    }

    public long getTotalNotifications() {
        return totalNotifications;
    }

    public long getReadNotifications() {
        return readNotifications;
    }

    public long getUnreadNotifications() {
        return unreadNotifications;
    }

    public double getReadPercentage() {
        if (totalNotifications == 0) return 0;
        return Math.round((readNotifications * 100.0) / totalNotifications * 100.0) / 100.0;
    }

    public Map<String, Long> getNotificationsByType() {
        return notificationsByType;
    }

    public Map<String, Long> getNotificationsByChannel() {
        return notificationsByChannel;
    }

    public double getAvgResponseTime() {
        return avgResponseTime;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalNotifications", totalNotifications);
        result.put("readNotifications", readNotifications);
        result.put("unreadNotifications", unreadNotifications);
        result.put("readPercentage", getReadPercentage());
        result.put("notificationsByType", notificationsByType);
        result.put("notificationsByChannel", notificationsByChannel);
        result.put("avgResponseTime", avgResponseTime);
        result.put("lastUpdated", lastUpdated);
        return result;
    }
}
