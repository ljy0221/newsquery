package com.newsquery.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class KeywordSubscription {
    private final String id;
    private final String userId;
    private final String keyword;
    private final boolean active;
    private final LocalDateTime createdAt;

    public KeywordSubscription(String userId, String keyword) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.keyword = keyword;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getKeyword() {
        return keyword;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
