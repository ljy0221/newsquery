package com.newsquery.notification;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {
    private final String id;
    private final String userId;
    private final String type;
    private final String message;
    private final LocalDateTime createdAt;
    private boolean read;

    public Notification(String userId, String type, String message, LocalDateTime createdAt) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
        this.read = false;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void markAsRead() {
        this.read = true;
    }
}
