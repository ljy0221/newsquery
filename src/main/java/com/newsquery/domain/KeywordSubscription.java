package com.newsquery.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "keyword_subscriptions", indexes = {
    @Index(name = "idx_keyword_subscriptions_user_id", columnList = "user_id")
})
public class KeywordSubscription {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "keyword", nullable = false)
    private String keyword;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public KeywordSubscription() {}

    public KeywordSubscription(String userId, String keyword) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.keyword = keyword;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getKeyword() { return keyword; }
    public boolean isActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setActive(boolean active) { this.isActive = active; }
}
