package com.newsquery.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phase 5: 저장된 검색 (Saved Query)
 * 사용자가 자주 사용하는 NQL 쿼리를 저장하고 재사용
 */
public class SavedQuery {

    private final String id;
    private final String userId;
    private final String nql;
    private final String name;
    private final String description;
    private final boolean isFavorite;
    private final int executionCount;
    private final double avgResponseTimeMs;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime lastExecutedAt;

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

    public SavedQuery(String id, String userId, String nql, String name, String description,
                      boolean isFavorite, int executionCount, double avgResponseTimeMs,
                      LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime lastExecutedAt) {
        this.id = id;
        this.userId = userId;
        this.nql = nql;
        this.name = name;
        this.description = description;
        this.isFavorite = isFavorite;
        this.executionCount = executionCount;
        this.avgResponseTimeMs = avgResponseTimeMs;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastExecutedAt = lastExecutedAt;
    }

    // Getters
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
