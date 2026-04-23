package com.newsquery.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phase 5.1: 저장된 검색 (Saved Query)
 * PostgreSQL JPA 엔티티로 전환
 */
@Entity
@Table(name = "saved_queries", indexes = {
    @Index(name = "idx_saved_queries_user_id", columnList = "user_id")
})
public class SavedQuery {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "nql", columnDefinition = "TEXT", nullable = false)
    private String nql;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_favorite")
    private boolean isFavorite;

    @Column(name = "execution_count")
    private int executionCount;

    @Column(name = "avg_response_time_ms")
    private double avgResponseTimeMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    public SavedQuery() {}

    public SavedQuery(String userId, String nql, String name, String description) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.nql = nql;
        this.name = name;
        this.description = description;
        this.isFavorite = false;
        this.executionCount = 0;
        this.avgResponseTimeMs = 0.0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastExecutedAt = null;
    }

    // Getters & Setters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getNql() { return nql; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isFavorite() { return isFavorite; }
    public int getExecutionCount() { return executionCount; }
    public double getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getLastExecutedAt() { return lastExecutedAt; }

    public void setFavorite(boolean favorite) { this.isFavorite = favorite; }
    public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }
    public void setAvgResponseTimeMs(double avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }
    public void setLastExecutedAt(LocalDateTime lastExecutedAt) { this.lastExecutedAt = lastExecutedAt; }

    @Override
    public String toString() {
        return "SavedQuery{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", nql='" + nql + '\'' +
                ", executionCount=" + executionCount +
                ", avgResponseTimeMs=" + avgResponseTimeMs +
                '}';
    }
}
