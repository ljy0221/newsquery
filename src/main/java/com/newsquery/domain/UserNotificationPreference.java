package com.newsquery.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phase 7: 사용자 알림 설정
 * 사용자가 알림을 받을 채널, 시간대 등을 설정
 */
public class UserNotificationPreference {
    private final String id;
    private final String userId;
    private boolean emailEnabled;
    private boolean pushEnabled;
    private boolean smsEnabled;
    private String quietHoursStart;  // HH:mm
    private String quietHoursEnd;    // HH:mm
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserNotificationPreference(String userId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.emailEnabled = true;
        this.pushEnabled = true;
        this.smsEnabled = false;
        this.quietHoursStart = "22:00";
        this.quietHoursEnd = "08:00";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    public void setSmsEnabled(boolean smsEnabled) {
        this.smsEnabled = smsEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public String getQuietHoursStart() {
        return quietHoursStart;
    }

    public void setQuietHoursStart(String quietHoursStart) {
        this.quietHoursStart = quietHoursStart;
        this.updatedAt = LocalDateTime.now();
    }

    public String getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHoursEnd(String quietHoursEnd) {
        this.quietHoursEnd = quietHoursEnd;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isInQuietHours() {
        // Phase 7.1: 현재 시간이 조용한 시간대인지 확인
        LocalDateTime now = LocalDateTime.now();
        String currentTime = String.format("%02d:%02d", now.getHour(), now.getMinute());

        if (quietHoursStart.compareTo(quietHoursEnd) < 0) {
            // 예: 22:00 ~ 08:00 (다음날)
            return currentTime.compareTo(quietHoursStart) >= 0 ||
                   currentTime.compareTo(quietHoursEnd) < 0;
        } else {
            // 예: 08:00 ~ 22:00
            return currentTime.compareTo(quietHoursStart) >= 0 &&
                   currentTime.compareTo(quietHoursEnd) < 0;
        }
    }
}
