package com.newsquery.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 알림 도메인 모델
 * SSE 이벤트로 클라이언트에 전송되는 알림 정보
 */
public class Notification {
    private final String id;
    private final String userId;
    private final String type;
    private final String message;
    private final LocalDateTime createdAt;
    private final Map<String, Object> details;
    private boolean read;

    public Notification(String userId, String type, String message, LocalDateTime createdAt) {
        this(userId, type, message, createdAt, null);
    }

    public Notification(String userId, String type, String message, LocalDateTime createdAt, Map<String, Object> details) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
        this.details = details;
        this.read = false;
    }

    // Getters
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("userId")
    public String getUserId() {
        return userId;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    @JsonProperty("createdAt")
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("details")
    public Map<String, Object> getDetails() {
        return details;
    }

    @JsonProperty("read")
    public boolean isRead() {
        return read;
    }

    public void markAsRead() {
        this.read = true;
    }
}
