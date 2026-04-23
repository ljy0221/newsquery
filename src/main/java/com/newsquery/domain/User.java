package com.newsquery.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Phase 5.1: 사용자 엔티티
 * PostgreSQL 기반 영구 저장
 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User() {}

    public User(String userId, String email) {
        this.userId = userId;
        this.email = email;
        this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
